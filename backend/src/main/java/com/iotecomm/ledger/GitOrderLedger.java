package com.iotecomm.ledger;

import com.iotecomm.config.IotEcommProperties;
import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Stores orders as Markdown in a git repository instead of a database.
 * <pre>
 *   ORDERS.md              index table of all orders (newest first)
 *   orders/&lt;orderId&gt;.md   one file per order: YAML front matter + items table
 * </pre>
 * Every change is a git commit; if a remote is configured it is pushed asynchronously.
 * On startup the existing order files are read back so state survives restarts.
 */
@Component
public class GitOrderLedger {
    private static final Logger log = LoggerFactory.getLogger(GitOrderLedger.class);
    private static final String ORDERS_DIR = "orders";
    private static final String INDEX_FILE = "ORDERS.md";

    private final IotEcommProperties.Ledger cfg;
    private final String currency;
    private final Path root;
    private final Git git;
    private final Map<UUID, OrderRecord> orders = new HashMap<>();
    private final ExecutorService pushExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ledger-push"));

    public GitOrderLedger(IotEcommProperties props) throws Exception {
        this.cfg = props.ledger();
        this.currency = props.currency();
        this.root = Path.of(cfg.dir()).toAbsolutePath().normalize();
        this.git = openOrInit();
        loadExisting();
        log.info("Order ledger at {} ({} orders loaded, remote: {})", root, orders.size(),
                hasRemote() ? cfg.remoteUrl() : "none");
    }

    // ------------------------------------------------------------------ public API

    /** Adds an order. Returns false if an order with the same id or (device, clientOrderId) already exists. */
    public synchronized boolean add(OrderRecord order) throws Exception {
        boolean duplicate = orders.containsKey(order.id()) || orders.values().stream()
                .anyMatch(o -> o.deviceId().equals(order.deviceId()) && o.clientOrderId().equals(order.clientOrderId()));
        if (duplicate) return false;
        orders.put(order.id(), order);
        writeOrder(order);
        commit("New order %s from %s: %s".formatted(shortId(order.id()), order.deviceId(), money(order.totalCents())));
        return true;
    }

    public synchronized OrderRecord updateStatus(UUID id, OrderStatus status) throws Exception {
        OrderRecord current = orders.get(id);
        if (current == null) throw new NoSuchElementException("Order not found: " + id);
        if (current.status() == status) return current;
        OrderRecord updated = current.withStatus(status);
        orders.put(id, updated);
        writeOrder(updated);
        commit("Order %s: %s -> %s".formatted(shortId(id), current.status(), status));
        return updated;
    }

    public synchronized long openOrderCount(String deviceId) {
        return orders.values().stream()
                .filter(o -> o.deviceId().equals(deviceId) && OrderStatus.OPEN.contains(o.status()))
                .count();
    }

    public synchronized List<OrderRecord> list(OrderStatus status) {
        return orders.values().stream()
                .filter(o -> status == null || o.status() == status)
                .sorted(Comparator.comparing(OrderRecord::createdAt).reversed())
                .toList();
    }

    // ------------------------------------------------------------------ git

    private Git openOrInit() throws Exception {
        File dir = root.toFile();
        if (new File(dir, ".git").exists()) {
            return Git.open(dir);
        }
        boolean empty = !dir.exists() || Objects.requireNonNull(dir.list()).length == 0;
        if (hasRemote() && empty) {
            try {
                log.info("Cloning order ledger from {}", cfg.remoteUrl());
                return Git.cloneRepository().setURI(cfg.remoteUrl()).setDirectory(dir)
                        .setBranch(cfg.branch()).setCredentialsProvider(credentials()).call();
            } catch (Exception e) {
                log.warn("Clone failed ({}), initialising a fresh ledger repo instead", e.getMessage());
                deleteRecursively(root);
            }
        }
        Files.createDirectories(root);
        Git g = Git.init().setDirectory(dir).setInitialBranch(cfg.branch()).call();
        if (hasRemote()) {
            g.remoteAdd().setName("origin").setUri(new URIish(cfg.remoteUrl())).call();
        }
        return g;
    }

    private void commit(String message) throws Exception {
        writeIndex();
        git.add().addFilepattern(ORDERS_DIR).addFilepattern(INDEX_FILE).call();
        git.commit().setMessage(message)
                .setAuthor(cfg.authorName(), cfg.authorEmail())
                .setCommitter(cfg.authorName(), cfg.authorEmail())
                .call();
        log.info("Ledger commit: {}", message);
        if (hasRemote()) pushExecutor.submit(this::push);
    }

    private void push() {
        try {
            Iterable<PushResult> results = git.push().setRemote("origin")
                    .setRefSpecs(new RefSpec("refs/heads/" + cfg.branch() + ":refs/heads/" + cfg.branch()))
                    .setCredentialsProvider(credentials())
                    .call();
            for (PushResult r : results) {
                for (RemoteRefUpdate u : r.getRemoteUpdates()) {
                    if (u.getStatus() != RemoteRefUpdate.Status.OK && u.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                        log.warn("Ledger push to {} was {}: {}", u.getRemoteName(), u.getStatus(), u.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Commits stay local and are included in the next successful push.
            log.warn("Ledger push failed: {}", e.getMessage());
        }
    }

    private boolean hasRemote() {
        return cfg.remoteUrl() != null && !cfg.remoteUrl().isBlank();
    }

    private CredentialsProvider credentials() {
        if (cfg.token() == null || cfg.token().isBlank()) return null;
        String user = cfg.username() == null || cfg.username().isBlank() ? "git" : cfg.username();
        return new UsernamePasswordCredentialsProvider(user, cfg.token());
    }

    @PreDestroy
    void close() {
        pushExecutor.shutdown();
        git.close();
    }

    // ------------------------------------------------------------------ markdown write

    private void writeOrder(OrderRecord o) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("---\n")
          .append("orderId: ").append(o.id()).append('\n')
          .append("deviceId: ").append(o.deviceId()).append('\n')
          .append("customer: ").append(oneLine(o.customerName())).append('\n')
          .append("clientOrderId: ").append(o.clientOrderId()).append('\n')
          .append("status: ").append(o.status()).append('\n')
          .append("totalCents: ").append(o.totalCents()).append('\n')
          .append("currency: ").append(currency).append('\n')
          .append("createdAt: ").append(o.createdAt()).append('\n')
          .append("updatedAt: ").append(o.updatedAt()).append('\n')
          .append("---\n\n")
          .append("# Order ").append(shortId(o.id())).append("\n\n")
          .append("- **Status:** ").append(o.status()).append('\n')
          .append("- **Customer:** ").append(o.customerName()).append(" (`").append(o.deviceId()).append("`)\n")
          .append("- **Placed:** ").append(o.createdAt()).append('\n')
          .append("- **Updated:** ").append(o.updatedAt()).append("\n\n")
          .append("## Items\n\n")
          .append("| SKU | Product | Qty | Unit price | Line total |\n")
          .append("|-----|---------|----:|-----------:|-----------:|\n");
        for (var i : o.items()) {
            md.append("| ").append(i.sku()).append(" | ").append(cell(i.name())).append(" | ").append(i.quantity())
              .append(" | ").append(decimal(i.unitPriceCents())).append(" | ")
              .append(decimal(i.unitPriceCents() * i.quantity())).append(" |\n");
        }
        md.append("\n**Total: ").append(money(o.totalCents())).append("**\n");

        Path file = root.resolve(ORDERS_DIR).resolve(o.id() + ".md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, md.toString(), StandardCharsets.UTF_8);
    }

    private void writeIndex() throws IOException {
        StringBuilder md = new StringBuilder("# Orders\n\n");
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        orders.values().forEach(o -> counts.merge(o.status(), 1L, Long::sum));
        md.append("Total orders: **").append(orders.size()).append("**");
        counts.forEach((s, c) -> md.append(" · ").append(s).append(": ").append(c));
        md.append("\n\n| Order | Placed (UTC) | Device | Customer | Items | Total | Status |\n")
          .append("|-------|--------------|--------|----------|-------|------:|--------|\n");
        for (OrderRecord o : list(null)) {
            String items = String.join(", ", o.items().stream().map(i -> i.quantity() + "× " + cell(i.name())).toList());
            md.append("| [").append(shortId(o.id())).append("](").append(ORDERS_DIR).append('/').append(o.id()).append(".md) | ")
              .append(o.createdAt().toString(), 0, 16).append(" | `").append(o.deviceId()).append("` | ")
              .append(cell(o.customerName())).append(" | ").append(items).append(" | ")
              .append(money(o.totalCents())).append(" | ").append(o.status()).append(" |\n");
        }
        Files.writeString(root.resolve(INDEX_FILE), md.toString(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ markdown read (startup)

    private void loadExisting() throws IOException {
        Path dir = root.resolve(ORDERS_DIR);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                try {
                    OrderRecord o = parse(Files.readAllLines(f, StandardCharsets.UTF_8));
                    orders.put(o.id(), o);
                } catch (Exception e) {
                    log.warn("Skipping unreadable order file {}: {}", f.getFileName(), e.getMessage());
                }
            }
        }
    }

    private static OrderRecord parse(List<String> lines) {
        Map<String, String> fm = new HashMap<>();
        int i = 0;
        if (!lines.get(i++).equals("---")) throw new IllegalArgumentException("missing front matter");
        for (; i < lines.size() && !lines.get(i).equals("---"); i++) {
            int c = lines.get(i).indexOf(':');
            if (c > 0) fm.put(lines.get(i).substring(0, c).trim(), lines.get(i).substring(c + 1).trim());
        }
        List<OrderRecord.Item> items = new ArrayList<>();
        boolean inTable = false;
        for (; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith("| SKU |")) { inTable = true; continue; }
            if (!inTable || l.startsWith("|---")) continue;
            if (!l.startsWith("|")) break;
            String[] cells = l.substring(1, l.length() - 1).split("(?<!\\\\)\\|");
            items.add(new OrderRecord.Item(cells[0].trim(), cells[1].trim().replace("\\|", "|"),
                    Integer.parseInt(cells[2].trim()), new BigDecimal(cells[3].trim()).movePointRight(2).intValueExact()));
        }
        return new OrderRecord(UUID.fromString(fm.get("orderId")), fm.get("deviceId"), fm.get("customer"),
                fm.get("clientOrderId"), OrderStatus.valueOf(fm.get("status")), Integer.parseInt(fm.get("totalCents")),
                Instant.parse(fm.get("createdAt")), Instant.parse(fm.get("updatedAt")), items);
    }

    // ------------------------------------------------------------------ helpers

    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static String decimal(int cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    private String money(int cents) { return currency + " " + decimal(cents); }
    private static String cell(String s) { return s == null ? "" : s.replace("|", "\\|"); }
    private static String oneLine(String s) { return s == null ? "" : s.replace('\n', ' '); }

    private static void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        }
    }
}

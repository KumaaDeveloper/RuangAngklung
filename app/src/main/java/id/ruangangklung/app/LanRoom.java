package id.ruangangklung.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Players send note events to a single speaker over local Wi-Fi. */
public final class LanRoom implements Closeable {
    public static final int PORT = 38245;
    private static final int PROTOCOL_VERSION = 4;
    private static final byte HELLO = 1, SNAPSHOT = 2, PLAY = 3, UPDATE = 4,
            ERROR = 5, CLOSED = 6, PING = 7, PONG = 8;
    private static final int MAX_FRAME = 8192;
    private static final long MIN_PLAY_INTERVAL_NS = 55_000_000L;

    public interface Listener {
        /** All callbacks run on a worker thread; UI callers should use runOnUiThread. */
        void onReady();
        void onSnapshot(List<Member> members);
        /** Called on the speaker only; player phones never receive audio events. */
        void onRemotePlay(String memberId, String note);
        void onClosed(String reason);
        /** Temporary transport loss while automatic reconnection is in progress. */
        default void onStatus(String status) { }
    }

    public static final class Member {
        public final String id;
        public final String name;
        public final List<String> notes;
        public final boolean speaker;

        private Member(String id, String name, List<String> notes, boolean speaker) {
            this.id = id;
            this.name = name;
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
            this.speaker = speaker;
        }
    }

    private final boolean hosting;
    private final String address;
    private final String ownId = UUID.randomUUID().toString();
    private final String ownName;
    private final Listener listener;
    private final Object membersLock = new Object();
    private final Object publishLock = new Object();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private final Map<String, Peer> activePeers = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final ExecutorService outbound = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private volatile ServerSocket server;
    private volatile Peer clientPeer;
    private volatile boolean closed;
    private volatile boolean ready;
    private volatile List<String> ownNotes;

    private LanRoom(boolean hosting, String address, String name, List<String> notes, Listener listener) {
        if (hosting ? notes == null || !notes.isEmpty() : !NoteCatalog.validSelection(notes, 3))
            throw new IllegalArgumentException("Pemain memilih 1–3 nada; speaker tidak memilih nada");
        this.hosting = hosting;
        this.address = address;
        this.ownName = cleanName(name);
        this.ownNotes = NoteCatalog.order(notes);
        this.listener = listener;
        if (hosting) members.put(ownId, new Member(ownId, ownName, ownNotes, true));
        else heartbeat.scheduleAtFixedRate(this::sendPing, 3, 3, TimeUnit.SECONDS);
    }

    public static LanRoom host(String name, Listener listener) {
        LanRoom room = new LanRoom(true, null, name, Collections.emptyList(), listener);
        room.io.execute(room::serve);
        return room;
    }

    public static LanRoom join(String ipv4, String name, List<String> notes, Listener listener) {
        if (!validIpv4(ipv4)) throw new IllegalArgumentException("Masukkan alamat IP IPv4 ruang yang benar");
        LanRoom room = new LanRoom(false, ipv4, name, notes, listener);
        room.io.execute(room::connect);
        return room;
    }

    public String ownId() { return ownId; }
    public boolean isHost() { return hosting; }
    public boolean isReady() { return ready && !closed; }

    private void serve() {
        try (ServerSocket listen = new ServerSocket()) {
            server = listen;
            listen.setReuseAddress(true);
            listen.bind(new InetSocketAddress(PORT));
            if (closed) return;
            ready = true;
            listener.onReady();
            publishSnapshot();
            while (!closed) {
                Socket socket = listen.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(8000);
                Peer peer = new Peer(socket);
                peers.add(peer);
                io.execute(() -> servePeer(peer));
            }
        } catch (IOException exception) {
            if (!closed) listener.onClosed("Gagal membuka ruang. Port mungkin sedang dipakai.");
        }
    }

    private void servePeer(Peer peer) {
        boolean joined = false;
        try {
            Frame hello = peer.read();
            if (hello.type != HELLO) throw new IOException("Sapaan tidak dikenal");
            int version = hello.data.readUnsignedByte();
            String id = hello.data.readUTF();
            String name = hello.data.readUTF();
            List<String> notes = readNotes(hello.data, false);
            if (version != PROTOCOL_VERSION) {
                peer.send(ERROR, data -> data.writeUTF("Versi ruang berbeda. Pasang Ruang Angklung 1.6.0 di semua ponsel."));
                return;
            }
            if (!id.matches("[0-9a-fA-F\\-]{36}") || name.trim().isEmpty()
                    || name.length() > 24 || !NoteCatalog.validSelection(notes, 3)) {
                peer.send(ERROR, data -> data.writeUTF("Nama atau pilihan nada tidak valid."));
                return;
            }
            if (closed) return;
            peer.id = id;
            synchronized (membersLock) {
                if (!members.containsKey(id) && members.size() >= 128) {
                    peer.send(ERROR, data -> data.writeUTF("Ruang penuh atau peserta sudah terhubung."));
                    return;
                }
                Peer stale = activePeers.put(id, peer);
                if (stale != null && stale != peer) stale.close();
                members.put(id, new Member(id, name.trim(), NoteCatalog.order(notes), false));
                joined = true;
            }
            peer.socket.setSoTimeout(12000);
            publishSnapshot();
            while (!closed) {
                Frame frame = peer.read();
                if (frame.type == UPDATE) {
                    List<String> changed = readNotes(frame.data, false);
                    if (!NoteCatalog.validSelection(changed, 3)) {
                        peer.send(ERROR, data -> data.writeUTF("Pilih 1–3 nada berbeda."));
                        continue;
                    }
                    synchronized (membersLock) {
                        Member current = members.get(peer.id);
                        if (current != null) members.put(peer.id, new Member(peer.id, current.name, NoteCatalog.order(changed), false));
                    }
                    publishSnapshot();
                } else if (frame.type == PLAY) {
                    String note = frame.data.readUTF();
                    boolean allowed;
                    synchronized (membersLock) {
                        Member member = members.get(peer.id);
                        allowed = member != null && member.notes.contains(note);
                    }
                    long now = System.nanoTime();
                    if (allowed && now - peer.lastPlayNs >= MIN_PLAY_INTERVAL_NS) {
                        peer.lastPlayNs = now;
                        listener.onRemotePlay(peer.id, note);
                    }
                } else if (frame.type == PING) {
                    peer.send(PONG, data -> { });
                } else {
                    throw new IOException("Pesan tidak dikenal");
                }
            }
        } catch (IOException ignored) {
            // A departed guest simply disappears from the room list.
        } finally {
            peer.close();
            peers.remove(peer);
            if (joined && activePeers.remove(peer.id, peer)) {
                synchronized (membersLock) { members.remove(peer.id); }
                if (!closed) publishSnapshot();
            }
        }
    }

    private void connect() {
        String reason = "Koneksi ke ruang terputus.";
        boolean connectedBefore = false;
        boolean terminal = false;
        int failed = 0;
        while (!closed && failed < 6 && !terminal) {
            Peer peer = null;
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(address, PORT), 3000);
                socket.setSoTimeout(8000);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                peer = new Peer(socket);
                clientPeer = peer;
                List<String> initial = ownNotes;
                peer.send(HELLO, data -> {
                    data.writeByte(PROTOCOL_VERSION);
                    data.writeUTF(ownId);
                    data.writeUTF(ownName);
                    writeNotes(data, initial);
                });
                Frame first = peer.read();
                if (first.type == ERROR) {
                    reason = first.data.readUTF();
                    terminal = true;
                } else if (first.type != SNAPSHOT) {
                    throw new IOException("Ruang tidak merespons dengan benar");
                } else {
                    processSnapshot(first.data);
                    if (closed) break;
                    peer.socket.setSoTimeout(12000);
                    ready = true;
                    if (connectedBefore) listener.onStatus("Terhubung kembali ke speaker.");
                    listener.onReady();
                    connectedBefore = true;
                    failed = 0;
                    while (!closed) {
                        Frame frame = peer.read();
                        if (frame.type == SNAPSHOT) processSnapshot(frame.data);
                        else if (frame.type == PONG) { /* Heartbeat acknowledged. */ }
                        else if (frame.type == ERROR) listener.onStatus(frame.data.readUTF());
                        else if (frame.type == CLOSED) {
                            reason = "Penyedia ruang menutup ruang.";
                            terminal = true;
                            break;
                        } else throw new IOException("Pesan tidak dikenal");
                    }
                }
            } catch (IOException exception) {
                reason = connectedBefore ? "Koneksi ke speaker terputus. Periksa Wi-Fi dan alamat IP ruang."
                        : "Tidak bisa terhubung. Periksa IP speaker dan Wi-Fi yang sama.";
            } finally {
                ready = false;
                if (clientPeer == peer) clientPeer = null;
                if (peer != null) peer.close();
                else if (socket != null) try { socket.close(); } catch (IOException ignored) { }
            }
            if (closed || terminal) break;
            failed++;
            if (failed >= 6) break;
            listener.onStatus(connectedBefore
                    ? "Koneksi terputus, menyambung ulang… (" + failed + "/5)"
                    : "Mencari speaker di Wi-Fi yang sama… (" + failed + "/5)");
            try { Thread.sleep(Math.min(500L * failed, 2000L)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        if (!closed) listener.onClosed(reason);
    }

    private void sendPing() {
        if (hosting || !isReady()) return;
        Peer peer = clientPeer;
        if (peer == null) return;
        try { peer.send(PING, data -> { }); }
        catch (IOException exception) { peer.close(); }
    }

    public void updateNotes(List<String> changed) {
        if (hosting) throw new IllegalStateException("Speaker tidak memilih nada");
        if (!NoteCatalog.validSelection(changed, 3)) throw new IllegalArgumentException("Pilih 1–3 nada berbeda");
        List<String> selected = NoteCatalog.order(changed);
        ownNotes = selected;
        if (!isReady()) return;
        outbound.execute(() -> {
            if (closed) return;
            Peer peer = clientPeer;
            if (peer != null) try { peer.send(UPDATE, data -> writeNotes(data, selected)); }
            catch (IOException exception) { peer.close(); }
        });
    }

    public void play(String note) {
        if (hosting || !ownNotes.contains(note) || !isReady()) return;
        outbound.execute(() -> {
            if (closed) return;
            Peer peer = clientPeer;
            if (peer != null) try { peer.send(PLAY, data -> data.writeUTF(note)); }
            catch (IOException exception) { peer.close(); }
        });
    }

    private void publishSnapshot() {
        synchronized (publishLock) {
            List<Member> snapshot;
            synchronized (membersLock) { snapshot = new ArrayList<>(members.values()); }
            listener.onSnapshot(snapshot);
            for (Peer peer : peers) {
                if (peer.id == null) continue;
                try { peer.send(SNAPSHOT, data -> writeSnapshot(data, snapshot)); }
                catch (IOException exception) { peer.close(); }
            }
        }
    }

    private void processSnapshot(DataInputStream data) throws IOException {
        int count = data.readInt();
        if (count < 1 || count > 128) throw new IOException("Daftar peserta tidak valid");
        List<Member> snapshot = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = data.readUTF(), name = data.readUTF();
            boolean speaker = data.readBoolean();
            List<String> notes = readNotes(data, speaker);
            if (name.length() > 24 || (speaker ? !notes.isEmpty() : !NoteCatalog.validSelection(notes, 3)))
                throw new IOException("Peserta tidak valid");
            snapshot.add(new Member(id, name, notes, speaker));
        }
        listener.onSnapshot(snapshot);
    }

    private static void writeSnapshot(DataOutputStream data, List<Member> snapshot) throws IOException {
        data.writeInt(snapshot.size());
        for (Member member : snapshot) {
            data.writeUTF(member.id);
            data.writeUTF(member.name);
            data.writeBoolean(member.speaker);
            writeNotes(data, member.notes);
        }
    }

    private static void writeNotes(DataOutputStream data, List<String> notes) throws IOException {
        data.writeByte(notes.size());
        for (String note : notes) data.writeUTF(note);
    }

    private static List<String> readNotes(DataInputStream data, boolean allowEmpty) throws IOException {
        int size = data.readUnsignedByte();
        if (size > 3 || (!allowEmpty && size < 1)) throw new IOException("Jumlah nada tidak valid");
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < size; i++) notes.add(data.readUTF());
        return notes;
    }

    public static boolean validIpv4(String input) {
        if (input == null) return false;
        String[] pieces = input.trim().split("\\.", -1);
        if (pieces.length != 4) return false;
        for (String piece : pieces) {
            if (piece.isEmpty() || piece.length() > 3 || !piece.matches("[0-9]+")) return false;
            int value = Integer.parseInt(piece);
            if (value < 0 || value > 255) return false;
        }
        return true;
    }

    /** Prefer Wi-Fi addresses; on Android devices with hotspot, its local IPv4 also appears. */
    public static List<String> localAddresses() {
        List<String> preferred = new ArrayList<>();
        List<String> others = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        String ip = address.getHostAddress();
                        String name = network.getName().toLowerCase(java.util.Locale.ROOT);
                        List<String> destination = name.startsWith("wlan") || name.startsWith("ap")
                                || name.startsWith("eth") ? preferred : others;
                        if (!destination.contains(ip)) destination.add(ip);
                    }
                }
            }
        } catch (SocketException ignored) { }
        preferred.sort(Comparator.comparingInt(ip -> ip.startsWith("192.168.") ? 0 : 1));
        List<String> result = new ArrayList<>(preferred);
        result.addAll(others);
        return result;
    }

    private static String cleanName(String name) {
        String value = name == null ? "" : name.trim().replaceAll("[\\r\\n\\t]", " ");
        return value.isEmpty() ? "Pemain" : value.substring(0, Math.min(24, value.length()));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ready = false;
        if (hosting) for (Peer peer : peers) {
            try { peer.send(CLOSED, data -> {}); } catch (IOException ignored) { }
        }
        ServerSocket listening = server;
        if (listening != null) try { listening.close(); } catch (IOException ignored) { }
        Peer guest = clientPeer;
        if (guest != null) guest.close();
        for (Peer peer : peers) peer.close();
        peers.clear();
        activePeers.clear();
        io.shutdownNow();
        outbound.shutdownNow();
        heartbeat.shutdownNow();
    }

    private interface Payload { void write(DataOutputStream data) throws IOException; }

    private static final class Frame {
        final int type;
        final DataInputStream data;
        Frame(int type, DataInputStream data) { this.type = type; this.data = data; }
    }

    private static final class Peer implements Closeable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        volatile String id;
        volatile long lastPlayNs;

        Peer(Socket socket) throws IOException {
            this.socket = socket;
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        Frame read() throws IOException {
            int length = in.readInt();
            if (length < 1 || length > MAX_FRAME) throw new IOException("Panjang pesan tidak valid");
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            DataInputStream content = new DataInputStream(new ByteArrayInputStream(bytes));
            return new Frame(content.readUnsignedByte(), content);
        }

        synchronized void send(byte type, Payload payload) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(type);
            payload.write(data);
            data.flush();
            if (bytes.size() > MAX_FRAME) throw new IOException("Pesan terlalu panjang");
            out.writeInt(bytes.size());
            bytes.writeTo(out);
            out.flush();
        }

        @Override public void close() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}

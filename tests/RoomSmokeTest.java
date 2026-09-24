import id.ruangangklung.app.LanRoom;
import id.ruangangklung.app.MotionTuning;
import id.ruangangklung.app.NoteCatalog;
import id.ruangangklung.app.ShakeLogic;

import java.util.Arrays;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Integration smoke test with one speaker and two silent players over real TCP. */
public final class RoomSmokeTest {
    private static void check(boolean value, String explanation) {
        if (!value) throw new AssertionError(explanation);
    }

    private static boolean waitFor(CountDownLatch latch) throws InterruptedException {
        return latch.await(5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws Exception {
        check(NoteCatalog.validSelection(List.of("C4"), 3), "one group note");
        check(!NoteCatalog.validSelection(List.of("C4", "C4"), 3), "duplicate note rejected");
        check(!NoteCatalog.validSelection(List.of("C4", "D4", "E4", "F4"), 3), "group limit");
        check(NoteCatalog.label("C4").equals("C4 (1 · Do)"), "C4 is do in numbered notation");
        check(NoteCatalog.label("F#4").equals("F#4 (4♯ · Fa♯)"), "sharp note is named correctly");
        check(NoteCatalog.label("C5").equals("C5 (1↑ · Do↑)"), "higher octave is marked");
        check(NoteCatalog.solfegeFor("A#3").equals("La♯↓"), "lower chromatic note is named");
        check(NoteCatalog.solfegeFor("D5").equals("Re↑"), "upper note is named");
        String[] natural = {"C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"};
        String[] syllables = {"Do", "Re", "Mi", "Fa", "Sol", "La", "Si", "Do↑"};
        for (int i = 0; i < natural.length; i++)
            check(NoteCatalog.solfegeFor(natural[i]).equals(syllables[i]), "Do–Re–Mi mapping: " + natural[i]);
        check(!NoteCatalog.validSelection(List.of("C2"), 3), "obsolete synthetic note rejected");
        check(NoteCatalog.validSelection(Arrays.asList(NoteCatalog.NOTES).subList(0, 10), 10), "solo limit");
        check(!NoteCatalog.validSelection(Arrays.asList(NoteCatalog.NOTES).subList(0, 11), 10), "eleventh solo note");
        check(LanRoom.validIpv4("192.168.1.3") && !LanRoom.validIpv4("192.168.1.999"), "IP validation");
        check(!MotionTuning.enabled(0) && MotionTuning.enabled(1), "zero disables motion playback");
        check(MotionTuning.minimumGapNs(1) > MotionTuning.minimumGapNs(55)
                && MotionTuning.minimumGapNs(55) > MotionTuning.minimumGapNs(100),
                "low sensitivity slows repeated strikes");
        check(MotionTuning.gyroThreshold(1) > MotionTuning.gyroThreshold(100)
                && MotionTuning.accelerationThreshold(1) > MotionTuning.accelerationThreshold(100),
                "low sensitivity requires stronger movement");
        check(MotionTuning.minimumGapNs(100) == 90_000_000L, "high sensitivity stays responsive");
        ShakeLogic gated = new ShakeLogic(3.0f);
        check(gated.accept(4f, 0f, 0f, 1_000_000_000L), "first motion fires");
        check(!gated.accept(-4f, 0f, 0f, 1_100_000_000L, false), "cooldown suppresses event");
        check(gated.accept(-4f, 0f, 0f, 1_320_000_000L, true),
                "blocked event does not consume next deliberate shake");
        ShakeLogic shake = new ShakeLogic(2.0f);
        check(shake.accept(4.1f, 0f, 0f, 1_000_000_000L), "first strong swing");
        check(!shake.accept(4.1f, 0f, 0f, 1_010_000_000L), "held swing does not repeat");
        check(shake.accept(-4.1f, 0f, 0f, 1_085_000_000L), "fast reversal rings again");
        check(shake.accept(4.1f, 0f, 0f, 1_170_000_000L), "third fast swing");
        check(!shake.accept(4.1f, 0f, 0f, 1_260_000_000L), "constant spin is not repeated");
        shake.accept(.4f, 0f, 0f, 1_280_000_000L);
        check(shake.accept(2.2f, 0f, 0f, 1_390_000_000L), "new gesture after release");

        CountDownLatch hostReady = new CountDownLatch(1);
        CountDownLatch guestReady = new CountDownLatch(2);
        CountDownLatch everyoneConnected = new CountDownLatch(3);
        CountDownLatch hostHeard = new CountDownLatch(1);
        CountDownLatch secondNoteHeard = new CountDownLatch(1);
        CountDownLatch fastNotesHeard = new CountDownLatch(4);
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch rejoined = new CountDownLatch(1);
        CountDownLatch playedAfterReconnect = new CountDownLatch(1);
        CountDownLatch guestsClosed = new CountDownLatch(2);
        AtomicInteger playerAudioEvents = new AtomicInteger();
        AtomicInteger firstReadyCalls = new AtomicInteger();

        LanRoom host = LanRoom.host("Speaker", new LanRoom.Listener() {
            public void onReady() { hostReady.countDown(); }
            public void onSnapshot(List<LanRoom.Member> members) {
                if (members.size() == 3) everyoneConnected.countDown();
                if (!members.isEmpty()) check(members.get(0).speaker && members.get(0).notes.isEmpty(),
                        "speaker has no assigned notes");
            }
            public void onRemotePlay(String id, String note) {
                if (note.equals("D4")) { hostHeard.countDown(); fastNotesHeard.countDown(); }
                if (note.equals("G4")) secondNoteHeard.countDown();
                if (note.equals("E4")) playedAfterReconnect.countDown();
            }
            public void onClosed(String reason) { throw new AssertionError("Host closed: " + reason); }
        });
        LanRoom first = null, second = null;
        try {
            check(waitFor(hostReady), "host starts");
            first = LanRoom.join("127.0.0.1", "One", List.of("D4"), new LanRoom.Listener() {
                public void onReady() {
                    guestReady.countDown();
                    if (firstReadyCalls.incrementAndGet() > 1) rejoined.countDown();
                }
                public void onSnapshot(List<LanRoom.Member> members) {
                    if (members.size() == 3) everyoneConnected.countDown();
                    for (LanRoom.Member member : members)
                        if (member.name.equals("One") && member.notes.contains("E4")) updated.countDown();
                }
                public void onRemotePlay(String id, String note) { playerAudioEvents.incrementAndGet(); }
                public void onClosed(String reason) { guestsClosed.countDown(); }
            });
            second = LanRoom.join("127.0.0.1", "Two", List.of("G4"), new LanRoom.Listener() {
                public void onReady() { guestReady.countDown(); }
                public void onSnapshot(List<LanRoom.Member> members) {
                    if (members.size() == 3) everyoneConnected.countDown();
                }
                public void onRemotePlay(String id, String note) { playerAudioEvents.incrementAndGet(); }
                public void onClosed(String reason) { guestsClosed.countDown(); }
            });
            check(waitFor(guestReady), "two guests join");
            check(waitFor(everyoneConnected), "room roster reaches all three phones");
            first.play("D4");
            check(waitFor(hostHeard), "player note reaches speaker");
            second.play("G4");
            check(waitFor(secondNoteHeard), "other player's note reaches the same speaker");
            for (int i = 0; i < 3; i++) {
                Thread.sleep(90);
                first.play("D4");
            }
            check(waitFor(fastNotesHeard), "fast shakes remain audible at the speaker");
            check(playerAudioEvents.get() == 0, "player phones receive no audio events");
            first.updateNotes(List.of("E4"));
            check(waitFor(updated), "changed note propagated");
            Field transportField = LanRoom.class.getDeclaredField("clientPeer");
            transportField.setAccessible(true);
            ((Closeable) transportField.get(first)).close();
            check(rejoined.await(12, TimeUnit.SECONDS), "player reconnects after transport loss");
            check(guestsClosed.getCount() == 2, "brief disconnect does not eject player");
            first.play("E4");
            check(waitFor(playedAfterReconnect), "player's note reaches speaker after reconnect");
            host.close();
            check(waitFor(guestsClosed), "closing host returns guests to lobby");
            System.out.println("OK: fast shakes, speaker mix, roster, reconnect and close");
        } finally {
            host.close();
            if (first != null) first.close();
            if (second != null) second.close();
        }
    }
}

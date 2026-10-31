package io.github.mmarkimark.llartv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FixturesTest {
    static final String JSON = "{\"events\":[{\"date\":\"2026-11-01T15:00Z\",\"league\":{\"name\":\"Spanish LALIGA\"},"
            + "\"competitions\":[{\"competitors\":[{\"id\":\"83\",\"homeAway\":\"home\",\"team\":{\"id\":\"83\"}},"
            + "{\"id\":\"85\",\"homeAway\":\"away\",\"team\":{\"id\":\"85\",\"shortDisplayName\":\"Espanyol\"}}]}]}]}";

    @Test public void anOkResponseIsTheScheduleBody() throws Exception {
        FakeConnection c = new FakeConnection(200, JSON);
        String body = Fixtures.read(c);
        assertEquals("Espanyol", Fixtures.parse(body, "83").get(0).opponent);
        assertTrue(c.disconnected);
    }

    @Test public void aNonOkResponseIsAFailedFetchEvenWithAParsableBody() throws Exception {
        for (int code : new int[]{204, 302, 404, 503}) {
            FakeConnection c = new FakeConnection(code, JSON);
            try {
                Fixtures.read(c);
                fail("HTTP " + code + " must fail the fetch, so the cached matches and the retry rules apply");
            } catch (Exception expected) { /* HomeActivity's catch keeps the matches and sets fixFailed */ }
            assertTrue(c.disconnected);
        }
    }

    @Test public void aScheduleOverOneMebibyteFailsInsteadOfFillingTheMemory() throws Exception {
        FakeConnection c = FakeConnection.ofSize(1024 * 1024 + 1);
        try {
            Fixtures.read(c);
            fail("an oversized schedule must fail");
        } catch (Exception expected) { /* a failed fetch */ }
        assertTrue(c.disconnected);
    }

    @Test public void aScheduleOfExactlyTheLimitIsRead() throws Exception {
        assertEquals(1024 * 1024, Fixtures.read(FakeConnection.ofSize(1024 * 1024)).length());
    }
}

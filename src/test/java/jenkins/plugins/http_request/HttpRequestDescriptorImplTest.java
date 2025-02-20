package jenkins.plugins.http_request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * @author Martin Mosegaard Amdisen
 */
class HttpRequestDescriptorImplTest {

    private static final List<Integer> DEFAULT_VALID_RESPONSE_CODES_RANGE = streamToList(IntStream.rangeClosed(100, 399));

    @Test
    void parseToRangeShouldHandleEmptyString() {
        String value = "";
        List<IntStream> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(1, ranges.size());
        assertEquals(DEFAULT_VALID_RESPONSE_CODES_RANGE, streamToList(ranges.get(0)));
    }

    @Test
    void parseToRangeShouldHandleNull() {
        String value = null;
        List<IntStream> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(1, ranges.size());
        assertEquals(DEFAULT_VALID_RESPONSE_CODES_RANGE, streamToList(ranges.get(0)));
    }

    private static List<Integer> streamToList(IntStream stream) {
        return stream.boxed().toList();
    }
}

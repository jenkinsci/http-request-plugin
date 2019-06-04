package jenkins.plugins.http_request;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Range;
import com.google.common.collect.Ranges;

import org.junit.Test;

/**
 * @author Martin Mosegaard Amdisen
 */
public class HttpRequestDescriptorImplTest {

    private static final List<Range<Integer>> DEFAULT_VALID_RESPONSE_CODES_RANGE = Collections.singletonList(Ranges.closed(100, 399));

    @Test
    public void parseToRangeShouldHandleEmptyString() {
        String value = "";
        List<Range<Integer>> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(DEFAULT_VALID_RESPONSE_CODES_RANGE, ranges);
    }

    @Test
    public void parseToRangeShouldHandleNull() {
        String value = null;
        List<Range<Integer>> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(DEFAULT_VALID_RESPONSE_CODES_RANGE, ranges);
    }
}

package jenkins.plugins.http_request;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Range;

import org.junit.Test;

/**
 * @author Martin Mosegaard Amdisen
 */
public class HttpRequestDescriptorImplTest {

    @Test
    public void parseToRangeShouldHandleEmptyString() {
        String value = "";
        List<Range<Integer>> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(ranges, Collections.<Range<Integer>>emptyList());
    }

    @Test
    public void parseToRangeShouldHandleNull() {
        String value = null;
        List<Range<Integer>> ranges = HttpRequest.DescriptorImpl.parseToRange(value);
        assertEquals(ranges, Collections.<Range<Integer>>emptyList());
    }
}

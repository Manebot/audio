package io.manebot.plugin.audio.mixer.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MuxedMultiChannelFilter extends AbstractFilter implements MultiChannelFilter {
    private final SingleChannelFilter[] filters;

    public MuxedMultiChannelFilter(SingleChannelFilter... filters) {
        super(Arrays.stream(filters)
                .map(Filter::getSampleRate)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no filters"))
        );

        if (!Arrays.stream(filters).allMatch(filter -> filter.getSampleRate() == getSampleRate()))
            throw new IllegalArgumentException("all provided filters must be the same sample rate");

        this.filters = filters;
    }
    public MuxedMultiChannelFilter(Collection<SingleChannelFilter> filters) {
        super(filters.stream()
                .map(Filter::getSampleRate)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no filters"))
        );

        if (!filters.stream().allMatch(filter -> filter.getSampleRate() == getSampleRate()))
            throw new IllegalArgumentException("all provided filters must be the same sample rate");

        this.filters = filters.toArray(new SingleChannelFilter[0]);
    }

    @Override
    public int getChannels() {
        return filters.length;
    }

    @Override
    public int process(float[][] buffer, int offs, int len) {
        int n = 0;
        for (int ch = 0; ch < filters.length; ch ++)
            n += filters[ch].process(buffer[ch], offs, len);
        return n;
    }

    @Override
    public void reset() {
        for (Filter filter : filters) filter.reset();
    }

    public static MuxedMultiChannelFilter from(int channels, Function<Integer, SingleChannelFilter> callable) {
        return new MuxedMultiChannelFilter(
                IntStream.of(channels)
                .mapToObj(callable::apply)
                .collect(Collectors.toList())
        );
    }
}

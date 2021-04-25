package se.yarin.morphy.storage;

import org.jetbrains.annotations.NotNull;
import se.yarin.morphy.exceptions.MorphyInvalidDataException;
import se.yarin.util.ByteBufferUtil;

import java.nio.ByteBuffer;

public class FooBarItemSerializer implements ItemStorageSerializer<FooBarItemHeader, FooBarItem> {
    @Override
    public int serializedHeaderSize() {
        return 8;
    }

    @Override
    public long itemOffset(@NotNull FooBarItemHeader fooBarItemHeader, int index) {
        return headerSize(fooBarItemHeader) + (long) index * itemSize(fooBarItemHeader);
    }

    @Override
    public int itemSize(@NotNull FooBarItemHeader fooBarItemHeader) {
        return 34;
    }

    @Override
    public int headerSize(@NotNull FooBarItemHeader fooBarItemHeader) {
        return 8;
    }

    @Override
    public @NotNull FooBarItemHeader deserializeHeader(@NotNull ByteBuffer buf) throws MorphyInvalidDataException {
        return ImmutableFooBarItemHeader.builder()
                .version(ByteBufferUtil.getIntB(buf))
                .numItems(ByteBufferUtil.getIntB(buf))
                .build();
    }

    @Override
    public @NotNull FooBarItem deserializeItem(int id, @NotNull ByteBuffer buf) {
        return ImmutableFooBarItem.builder()
                .foo(ByteBufferUtil.getFixedSizeByteString(buf, 30))
                .bar(ByteBufferUtil.getIntB(buf))
                .build();
    }

    @Override
    public void serializeHeader(@NotNull FooBarItemHeader fooBarItemHeader, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putIntB(buf, fooBarItemHeader.version());
        ByteBufferUtil.putIntB(buf, fooBarItemHeader.numItems());
    }

    @Override
    public void serializeItem(@NotNull FooBarItem fooBarItem, @NotNull ByteBuffer buf) {
        ByteBufferUtil.putFixedSizeByteString(buf, fooBarItem.foo(), 30);
        ByteBufferUtil.putIntB(buf, fooBarItem.bar());
    }

    @Override
    public @NotNull FooBarItem emptyItem(int id) {
        return FooBarItem.empty();
    }
}

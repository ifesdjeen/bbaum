package com.ifesdjeen.bbaum;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class BTree<K, V> {

    private final String path;
    private final RandomAccessFile in;
    private final BTreeBuilder.ValueSerializer<K> keySerializer;
    private final BTreeBuilder.ValueSerializer<V> valueSerializer;
    private final K min;
    private final K max;
    private final int totalCount;
    private final int[] nodeOffsets;
    private final int fanout;
    private final int levels;

    public BTree(String path, BTreeBuilder.ValueSerializer<K> keySerializer, BTreeBuilder.ValueSerializer<V> valueSerializer) throws IOException {
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;

        this.path = path;
        this.in = new RandomAccessFile(new File(path), "rw");

        // Header serialization
        byte[] rootPageBytes = new byte[BTreeBuilder.BLOCK_SIZE];
        in.read(rootPageBytes);

        ByteBuffer byteBuffer = ByteBuffer.wrap(rootPageBytes);
        fanout = byteBuffer.getInt();
        totalCount = byteBuffer.getInt();
        levels = (int) Math.ceil(Math.log(totalCount) / Math.log(fanout));
        System.out.println("totalCount = " + totalCount);
        System.out.println("levels = " + levels);
        System.out.println("nodesOnLevel(0) = " + nodesOnLevel(0));
        System.out.println("nodesOnLevel(1) = " + nodesOnLevel(1));
        System.out.println("nodesOnLevel(2) = " + nodesOnLevel(2));
        System.out.println("nodesOnLevel(3) = " + nodesOnLevel(3));

        nodeOffsets = new int[byteBuffer.getInt()];
        this.min = keySerializer.deserialize(byteBuffer);
        this.max = keySerializer.deserialize(byteBuffer);

        for (int i = 0; i < nodeOffsets.length; i++) {
            this.nodeOffsets[i] = byteBuffer.getInt();
        }

        System.out.println("min = " + min);
        System.out.println("max = " + max);

        System.out.println("nodeOffsets = " + Arrays.toString(nodeOffsets));
    }

    /**
     * Returns the index of the first node in the levela
     */
    public int getLevelStartIndex(int level) {
        int levelStart = 0;
        for (int i = 0; i < level; i++) {
            levelStart += nodesOnLevel(i);
        }

        return levelStart;
    }

    /**
     * Calculates an amount of nodes for the given level
     */
    // TODO: use integer math
    public int nodesOnLevel(int level) {
        return (int) Math.pow(fanout, level);
    }

    /**
     * In order to locate every node, we only have to know which level it's on
     * (in order to skip to the level start), what is the index of it's parent
     * (to know how many nodes to skip to get to the first node that shares
     * the parent with the current one) and the index of node itself.
     */
    public ByteBuffer getNode(final int level, final int parentIndex, final int index) throws IOException {
        int startOffset;
        int blockSize;
        if (level == 0) {
            assert parentIndex == 0;
            startOffset = BTreeBuilder.BLOCK_SIZE * index;
            blockSize = nodeOffsets[1] - nodeOffsets[0];
        }
        else {
            int blockOffset = 0;
            for (int i = 0; i < level; i++) {
                blockOffset += nodesOnLevel(i);
            }

            blockOffset += fanout * parentIndex + index;
            System.out.println("blockOffset = " + blockOffset);
            System.out.println("nodesOnLevel(level) = " + (nodesOnLevel(level) - 1));
            System.out.println("in = " + index);

            System.out.println("(index < (nodesOnLevel(level) - 1)) = " + (index < (nodesOnLevel(level) - 1)));
            assert index < nodesOnLevel(level) : "Out of index";
            startOffset = blockOffset * BTreeBuilder.BLOCK_SIZE;
            blockSize = nodeOffsets[blockOffset + 1] - nodeOffsets[blockOffset]; // ??? last node won't work, we have to store the last node "end" block offset as well
            System.out.println("blockSize = " + blockSize);
        }

        ByteBuffer buf = ByteBuffer.allocate(blockSize * BTreeBuilder.BLOCK_SIZE);
        in.seek(startOffset);
        in.readFully(buf.array());
        return buf;
    }
}

package com.ifesdjeen.bbaum;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
    private final int[][] nodeOffsets;
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

        this.min = keySerializer.deserialize(byteBuffer);
        this.max = keySerializer.deserialize(byteBuffer);

        nodeOffsets = new int[levels][];
        for (int i = 0; i < levels; i++) {
            int levelSize = byteBuffer.getInt();
            nodeOffsets[i] = new int[levelSize];
            for (int j = 0; j < levelSize; j++) {
                this.nodeOffsets[i][j] = byteBuffer.getInt();
            }
        }

        System.out.println("min = " + min);
        System.out.println("max = " + max);

        for (int i = 0; i < levels; i++) {
            System.out.println("Level = " + Arrays.toString(nodeOffsets[i]));
        }
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

        int blockOffset = 0;
        for (int i = 0; i < level; i++) {
            blockOffset += nodesOnLevel(i);
        }

        blockOffset += fanout * parentIndex + index;

        int nodesOnCurrentLevel = nodesOnLevel(level);
        if (index > (nodesOnCurrentLevel - 1)) {
            throw new IndexOutOfBoundsException(String.format("Max nodes on %d level: %d, index: %d", nodesOnCurrentLevel, level, index));
        }

        startOffset = blockOffset * BTreeBuilder.BLOCK_SIZE;
        int[] levelOffsets = nodeOffsets[level];

        if (index == levelOffsets.length - 1) { // is't the last one in block
            blockSize = nodeOffsets[level + 1][0] - levelOffsets[index];
        } else if (level < levels - 1) {
            blockSize = levelOffsets[index + 1] - levelOffsets[index];
        } else {
            // ??? very last node won't work, we have to store the last node "end" block offset as well, we should do one more `else` block
            throw new NotImplementedException();
        }


        ByteBuffer buf = ByteBuffer.allocate(blockSize * BTreeBuilder.BLOCK_SIZE);
        in.seek(startOffset);
        in.readFully(buf.array());
        return buf;
    }
}

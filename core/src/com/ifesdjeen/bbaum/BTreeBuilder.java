package com.ifesdjeen.bbaum;

import javafx.util.Pair;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;

// TODO: fixed term fanout optimisation
// TODO: create from both sorted and unsorted data
// TODO: query modes?
// TODO: pre-calculate the size of the tree!!
public class BTreeBuilder<K extends Comparable, V> {

    private final static Iterator EMPTY_ITER = Collections.EMPTY_LIST.iterator();

    private final int fanout;
    private final ValueSerializer<K> keySerializer;
    private final ValueSerializer<V> valueSerializer;

    List<Level<K, V>> levels;
    LeafNode<K, V> currentLeaf;
    InternalNode<K, V> rightmostParent;
    int totalCount = 0;
    int totalNodes = 0;

    public BTreeBuilder(ValueSerializer<K> keySerializer, ValueSerializer<V> valueSerializer, int fanout) {
        this.fanout = fanout;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.levels = new ArrayList<>(8);
    }

    // TODO: memory estimate
    private static int memorySize() {
        return 0;
    }

    public static <T> T[] getArray(Class<T> klass, int size) {
        return (T[]) Array.newInstance(klass, size);
    }

    public void ingestSorted(SortedMap<K, V> source) {
        // Create the root node
        currentLeaf = makeLeaf();
        levels.add(new Level<K, V>(currentLeaf, levels.size()));
        rightmostParent = makeInternalNode();

        InternalNode<K, V> lastRoot = null;
        for (Map.Entry<K, V> kvEntry : source.entrySet()) {
            currentLeaf.add(kvEntry.getKey(), kvEntry.getValue());

            if (currentLeaf.isFull()) {
                rightmostParent.add(currentLeaf).ifPresent(p -> rightmostParent = p);
                if (lastRoot != rightmostParent.root()) {
                    lastRoot = rightmostParent.root();
                    levels.add(new Level<K, V>(lastRoot, levels.size()));
                }

                LeafNode<K, V> newLeaf = makeLeaf();
                currentLeaf.setNext(newLeaf);
                currentLeaf = newLeaf;
            }
            totalCount++;
        }

        if (!currentLeaf.isEmpty())
            rightmostParent.add(currentLeaf);
    }

    private LeafNode<K, V> makeLeaf() {
        totalNodes++;
        return makeLeaf(keySerializer, valueSerializer, fanout);
    }

    public static <K, V> LeafNode<K, V> makeLeaf(ValueSerializer<K> keySerializer, ValueSerializer<V> valueSerializer, int fanout) {
        return new LeafNode<K, V>(keySerializer, valueSerializer, fanout);
    }

    private InternalNode<K, V> makeInternalNode() {
        totalNodes++;
        return new InternalNode<K, V>(keySerializer, valueSerializer, fanout);
    }

    private static <K, V> InternalNode<K, V> makeInternalNode(InternalNode<K, V> prototype) {
        return new InternalNode<K, V>(prototype.keySerializer, prototype.valueSerializer, prototype.fanout);
    }

    // Serialise as much as fits into the block of 4K bytes.
    // One of the ways to lay things out is to first lay out the nodes (knowing how much will fit)
    // since each node is predictably-ordered, we can form the offset table in the end, which
    // will be loaded into the memory for further traversal

    // Another way to lay them out would be to pre-calculate the offsets upfront: for example,
    // by saying
    //
    public interface Serializer<K> {
        void serialize(ByteBuffer out) throws IOException;
        int sizeof();
        int blockSize();
    }

    public interface ValueSerializer<K> {
        void serialize(ByteBuffer out, K v) throws IOException;
        K deserialize(ByteBuffer out) throws IOException;
        int sizeof(K k);
    }

    private static void resetBuffer(ByteBuffer buffer) {
        buffer.flip();
        buffer.rewind();
        Arrays.fill(buffer.array(), (byte) 0);
        buffer.limit(BLOCK_SIZE);
    }

    public void serializeTree(OutputStream output) throws IOException {
        InternalNode<K, V> root = rightmostParent.root();
        ByteBuffer buffer = ByteBuffer.allocate(BLOCK_SIZE);

        buffer.putInt(fanout);
        buffer.putInt(totalCount);

        keySerializer.serialize(buffer, root.minKey());
        keySerializer.serialize(buffer, root.maxKey());

        // TODO: encode the node size in the header as well.
        // TODO: optimise
        {
            int blockOffset = 0;

            for (int i = levels.size() - 1; i >= 0; i--) {
                Level<K, V> level = levels.get(i);
                buffer.putInt(level.nodesInLevel(totalCount, fanout));
                for (Node<K, V> node : level) {
                    buffer.putInt(blockOffset);
                    blockOffset += node.blockSize();
                }
            }
//            Node<K, V> firstInLevel = root.iterator().next();
//            int level = 0;
//            while (firstInLevel != null) {
//                int nodesOnLevel = (int) Math.ceil(totalCount / Math.pow(fanout, levels.size() - level - 1));
//                System.out.println("nodesOnLevel = " + nodesOnLevel + " " + firstInLevel);
//                Iterator<Node<K,V>> levelIterator = firstInLevel.siblings();
//                while (levelIterator.hasNext()) {
//                    buffer.putInt(blockOffset);
//                    blockOffset += levelIterator.next().blockSize();
//                }
//
//                if (firstInLevel.isLeaf()) {
//                    break;
//                } else {
//                    firstInLevel = ((InternalNode<K,V>)firstInLevel).iterator().next();
//                    level++;
//                }
//            }


        }

//        System.out.println("ByteBufferUtil.prettyHexDump(buffer); = " + ByteBufferUtil.hexDump(buffer.array()));
        output.write(buffer.array());
        resetBuffer(buffer);

        root.serialize(buffer);
        resetBuffer(buffer);

        Node<K, V> firstInLevel = root.iterator().next(); // TODO: assert not empty
        while (firstInLevel != null)
        {
            Iterator<Node<K, V>> levelIterator = firstInLevel.siblings();

            while (levelIterator.hasNext()) {
                Node<K, V> currentNode = levelIterator.next();
                System.out.println("currentNode = " + currentNode);

                currentNode.serialize(buffer);
                output.write(buffer.array());
                resetBuffer(buffer);
            }

            if (firstInLevel.isLeaf()) {
                break;
            }
            else {
                firstInLevel = ((InternalNode<K, V>) firstInLevel).iterator().next();
            }
            System.out.println("== Level ==");
        }

    }

    public static abstract class Node<K, V> implements Serializer<Node<K, V>> {
        final int fanout;
        int count;
        private InternalNode<K, V> parent;
        private Node<K, V> next;

        public Node(int fanout) {
            this.count = 0;
            this.fanout = fanout;
        }

        public boolean isFull() {
            return count == fanout;
        }

        public Node<K, V> ifLeaf(Consumer<LeafNode<K, V>> c) {
            if (isLeaf())
                c.accept((LeafNode<K, V>) this);
            return this;
        }

        public Node<K, V> ifInternal(Consumer<InternalNode<K, V>> c) {
            if (!isLeaf())
                c.accept((InternalNode<K, V>) this);
            return this;
        }

        public boolean isEmpty() {
            return count == 0;
        }

        public abstract K minKey();

        public abstract K maxKey();

        public abstract boolean isLeaf();

        public InternalNode<K, V> parent() {
            return parent;
        }

        public InternalNode<K, V> root() {
            if (!isRoot())
                return parent.root();
            else
                return (InternalNode<K, V>) this;
        }

        public boolean isRoot() {
            return parent == null;
        }

        public void setParent(InternalNode<K, V> parent) {
            this.parent = parent;
        }

        public void setNext(Node<K, V> next) {
            assert this.next == null && isLeaf() == next.isLeaf();
            this.next = next;
        }

        public Node<K, V> next() {
            return next;
        }

        public Iterator<Node<K, V>> siblings() {

            return new Iterator<Node<K, V>>() {
                Node<K, V> next = Node.this;

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Node<K, V> next() {
                    Node<K, V> ret = next;
                    next = next.next();
                    return ret;
                }
            };
        }

        public abstract String toStringRecursive();


    }

    public static class InternalNode<K, V> extends Node<K, V> implements Iterable<Node<K, V>> {

        private final Node<K, V>[] nodes;
        private final ValueSerializer<K> keySerializer;
        private final ValueSerializer<V> valueSerializer;
//        private final int parentIdx;

        InternalNode(ValueSerializer<K> keySerializer, ValueSerializer<V> valueSerializer, int branchingFactor) {
            super(branchingFactor);

            this.nodes = getArray(Node.class, branchingFactor);
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
        }

        @Override
        public K minKey() {
            // TODO: denormalise??? maintain!
            assert !isEmpty() : "Empty node has no min key";
            return nodes[0].minKey();
        }

        @Override
        public K maxKey() {
            assert !isEmpty() : "Empty node has no max key";
            return nodes[count - 1].maxKey();
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        /**
         * Returns the new rightmost parent if the previous one was full
         */
        public Optional<InternalNode<K, V>> add(Node<K, V> childNode) {
            if (isFull()) {
                // If current root is full, start a new level
                if (parent() == null) { // TODO: handle useless root in case of a single node
                    setParent(makeInternalNode(this));
                    parent().add(this);
                }

                // If the current child node is full, create a new sibling
                InternalNode<K, V> newSibling = makeInternalNode(this);

                // and migrate the last written child node to this new sibling
                newSibling.add(childNode);
                parent().add(newSibling);
                // connect the level
                setNext(newSibling);

                // new rightmost parent will always be created on the rightmost
                // edge, which is closest to the leaves
                if (childNode.isLeaf())
                    return Optional.of(newSibling);
            } else {
                nodes[count++] = childNode;
                childNode.setParent(this);
            }

            return Optional.empty();
        }

        public Iterator<Node<K, V>> iterator() {
            return new Iterator<Node<K, V>>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < count;
                }

                @Override
                public Node<K, V> next() {
                    return nodes[i++];
                }
            };
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (parent() == null)
                builder.append("RootNode");
            else
                builder.append("InternalNode");

            builder.append("[");
            for (int i = 1; i < count; i++) {
                builder.append(nodes[i].minKey());
                if (i != (count - 1))
                    builder.append(", ");
            }
            builder.append("]");
            return builder.toString();
        }

        public String toStringRecursive() {
            StringBuilder builder = new StringBuilder();
            int depth;
            if (parent() == null) {
                builder.append("RootNode");
                depth = 0;
            } else {
                depth = 1;
                Node<K, V> parent = parent();
                while (parent != null) {
                    builder.append("\t");
                    parent = parent.parent();
                    depth++;
                }
                builder.append("InternalNode").append(fanout).append(' ');
            }


            builder.append("[");
            for (int i = 0; i < count; i++) {
                if (nodes[i] != null) //tood: remove
                    builder.append(nodes[i].minKey());
                if (i != (count - 1))
                    builder.append(", ");
            }
            builder.append("]");

            builder.append("\n");
            for (int i = 0; i < count; i++) {
                if (i == 0 && nodes[i].isLeaf()) {
                    for (int j = 0; j < depth; j++) {
                        builder.append("\t");
                    }
                }
                if (nodes[i] == null) // todo: remove
                    continue;

                builder.append(nodes[i].toStringRecursive());
                if (nodes[i].isLeaf() && i != (count - 1))
                    builder.append(", ");
            }

            if (parent() != null)
                builder.append("\n");
            return builder.toString();
        }

        /**
         * (byte info)
         * <p>
         * Next two are only present on in the root node:
         * ( short16 min_key_length) ( min_key_length bytes min_key)
         * ( short16 max_key_length) ( min_key_length bytes max_key)
         * <p>
         * ( short16 children_count )
         * children_count * [ (short16 key_length) ]
         * children_count * [ (key_length bytes key) ]
         * // THAT IS PULLED UP (children_count + 1) * [ (int32 child_offset) ]
         * <p>
         * TODO: fixed fanout keys
         * TODO: calculate offset in blocks!
         */
        @Override
        public void serialize(ByteBuffer out) throws IOException {
//            assert !isRoot(); // root should've been serialized in the header
            out.put((byte) (isRoot() ? 0 : 1));

            out.putShort((short) count);

            int childOffset = 0;
            // To simplify binary search, write offsets first and data entries after.
            // Skip the very first entry, since the search will check `<` sign only.
            // This allows the empty nodes, which will be handled by skipping to the
            // only child.
            for (Node<K, V> child : butFirst(this)) {
                out.putShort((short) childOffset);
                childOffset += keySerializer.sizeof(child.minKey()) + Short.BYTES;
            }

//            int blockOffset = 0;
            for (Node<K, V> child : butFirst(this)) {
                keySerializer.serialize(out, child.minKey());
                // we only need to know the index of the child. It's offset can be taken from the header.
//                out.putShort((short) blockOffset);
//                blockOffset += child.blockSize();
            }
        }

        @Override
        public int sizeof() {
            int size = Short.BYTES;
            if (isRoot()) {
                size += 2 * Short.BYTES;
            }

            size += Short.BYTES; // + (count - 1) * Short.BYTES;

            for (Node<K, V> child : butFirst(this)) {
                keySerializer.sizeof(child.minKey());
            }

            return size;
        }

        @Override
        public int blockSize() {
            int size = sizeof();
            return size < BLOCK_SIZE ? 1 : ((size / BLOCK_SIZE) + 1);
        }
    }

    public static class LeafNode<K, V> extends Node<K, V> implements Iterable<Pair<K, V>> {

        final K[] keys;
        final V[] vals;
        private final ValueSerializer<K> keySerializer;
        private final ValueSerializer<V> valueSerializer;

        private LeafNode(ValueSerializer<K> keySerializer, ValueSerializer<V> valueSerializer, int branchingFactor) {
            super(branchingFactor);
            keys = (K[])getArray(Object.class, branchingFactor);
            vals = (V[])getArray(Object.class, branchingFactor);
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
        }

        public K minKey() {
            assert !isEmpty() : "Empty leaf has no min key";
            return keys[0];
        }

        public K maxKey() {
            assert !isEmpty() : "Empty leaf has no max key";
            return keys[count - 1];
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        public Iterator<Pair<K, V>> iterator() {
            return new Iterator<Pair<K, V>>() {
                int i = 0;

                @Override
                public boolean hasNext() {
                    return i < count;
                }

                @Override
                public Pair<K, V> next() {
                    Pair<K, V> p = new Pair<K, V>(keys[i], vals[i]);
                    i++;
                    return p;
                }
            };
        }

        public String toStringRecursive() {
            return toString();
        }

        public void add(K k, V v) {
            assert !isFull() : "Cannot insert into the full node";
            keys[count] = k;
            vals[count] = v;
            count++;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder("LeafNode");
            builder.append(fanout).append(" [");

            for (int i = 0; i < count; i++) {
                builder.append(keys[i]).append(": ").append(vals[i]);
                if (i != (count - 1))
                    builder.append(", ");
            }

            builder.append("]");
            return builder.toString();
        }

        /**
         * (byte info)
         * <p>
         * <p>
         * ( short16 children_count )
         * children_count * [ (short16 key_length) ]
         * children_count * [ (key_length bytes key) ]
         * // THAT IS PULLED UP (children_count + 1) * [ (int32 child_offset) ]
         * <p>
         * TODO: fixed fanout keys
         * TODO: calculate offset in blocks!
         */
        @Override
        public void serialize(ByteBuffer out) throws IOException {
            out.put((byte) 2);

            out.putShort((short) count);
            int offset = 0;
            // Can we reduce an amount of iterations?
            for (Pair<K, V> kvPair : this) {
                out.putShort((short) offset);
                // TODO: 0 can be skipped
                offset += keySerializer.sizeof(kvPair.getKey());
                out.putShort((short) offset);
                offset += valueSerializer.sizeof(kvPair.getValue());
            }

            for (Pair<K, V> kvPair : this) {
                keySerializer.serialize(out, kvPair.getKey());
                valueSerializer.serialize(out, kvPair.getValue());
            }
        }

        @Override
        public int sizeof() {
            int size = 0;

            size += Byte.BYTES + Short.BYTES;

            for (Pair<K, V> kvPair : this) {
                // first loop
                size += 2 * Short.BYTES;
                size += keySerializer.sizeof(kvPair.getKey());
                size += valueSerializer.sizeof(kvPair.getValue());
            }

            return size;
        }

        @Override
        public int blockSize() {
            int size = sizeof();
            return size < BLOCK_SIZE ? 1 : ((size / BLOCK_SIZE) + 1);
        }
    }

    public static class Level<K, V> implements Iterable<Node<K, V>> {

        private final Node<K, V> firstInLevel;
        private final int level;

        public Level(Node<K, V> firstInLevel, int level) {
            this.firstInLevel = firstInLevel;
            this.level = level;
        }

        @Override
        public Iterator<Node<K, V>> iterator() {
            return firstInLevel.siblings();
        }

        public int nodesInLevel(int totalCount, int fanout) {
            return (int) Math.ceil(totalCount / Math.pow(fanout, level + 1));
        }

        public String toString() {
            return "Level (firstInLevel: " + firstInLevel + ")";
        }
    }

    // TODO: do dynamic block sizing! in order to calculate the dynamic block size, we should know
    // the sizes of the nodes and their occupancy. One approach would be to know how many blocks
    // each node takes and be able to skip to the node just based on its' sequence number and
    // use overflow blocks for the nodes that do not fit.
    //
    // The other one would be to just know how many blocks each node takes and have "block address"
    // assigned to it
    //
    // Inside one node, however, we will have a luxury of navigating in a much simpler way, since
    // we will only care about offsets inside the block

    // TODO: layout the nodes each one node per block; then the nodes are sequentially numbered and addressable
    // do overflow area for the nodes
    public static int BLOCK_SIZE = 4096;

    // TODO: optimise for the case of PointerNode, which has only one child

    public static <T> Iterable<T> butFirst(Iterable<T> orig) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                Iterator<T> origIter = orig.iterator();
                if (origIter.hasNext())
                    origIter.next();
                return new Iterator<T>() {
                    @Override
                    public boolean hasNext() {
                        return origIter.hasNext();
                    }

                    @Override
                    public T next() {
                        return origIter.next();
                    }
                };
            }
        };
    }
}
package com.ifesdjeen.bbaum;

import javafx.util.Pair;

import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

// TODO: fixed term fanout optimisation
// TODO: create from both sorted and unsorted data
// TODO: query modes?
public class BTreeBuilder<K extends Comparable, V> {

    private final static Iterator EMPTY_ITER = Collections.EMPTY_LIST.iterator();

    private final int fanout;
    private final Function<K, ByteBuffer> converter;
    private final Class<K> keyClass;
    private final Class<V> valueClass;
    LeafNode<K, V> currentLeaf;
    InternalNode<K, V> rightmostParent;

    public BTreeBuilder(Class<K> keyClass, Class<V> valueClass, int fanout, Function<K, ByteBuffer> converter) {
        this.fanout = fanout;
        this.converter = converter;
        this.keyClass = keyClass;
        this.valueClass = valueClass;
    }

    // TODO: memory fanout
    private static int memorySize() {
        return 0;
    }

    public static <T> T[] getArray(Class<T> klass, int size) {
        return (T[]) Array.newInstance(klass, size);
    }

    public void ingestSorted(SortedMap<K, V> source) {
        // Create the root node
        currentLeaf = makeLeaf();
        rightmostParent = makeInteriorNode();

        for (Map.Entry<K, V> kvEntry : source.entrySet()) {
            currentLeaf.add(kvEntry.getKey(), kvEntry.getValue());

            if (currentLeaf.isFull()) {
                rightmostParent.add(currentLeaf).ifPresent(nodes -> rightmostParent = nodes);
                LeafNode<K, V> newLeaf = makeLeaf();
                currentLeaf.setNext(newLeaf);
                currentLeaf = newLeaf;
            }
        }

        if (!currentLeaf.isEmpty())
            rightmostParent.add(currentLeaf);
    }

    private LeafNode<K, V> makeLeaf() {
        return new LeafNode<>(keyClass, valueClass, fanout);
    }

    private InternalNode<K, V> makeInteriorNode() {
        return new InternalNode<>(fanout);
    }

    /**
     * Leaves are serialized
     */

    // Serialise as much as fits into the block of 4K bytes.
    // One of the ways to lay things out is to first lay out the nodes (knowing how much will fit)
    // since each node is predictably-ordered, we can form the offset table in the end, which
    // will be loaded into the memory for further traversal

    // Another way to lay them out would be to pre-calculate the offsets upfront: for example,
    // by saying
    //
    public interface Serializer<K> {
        void serialize(DataOutput out, K k) throws IOException;

        int sizeof(K k);
    }

    public static abstract class Node<K, V> {
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

        public Node<K, V> ifInterior(Consumer<InternalNode<K, V>> c) {
            if (!isLeaf())
                c.accept((InternalNode) this);
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

        public InternalNode(int branchingFactor) {
            super(branchingFactor);
            nodes = getArray(Node.class, branchingFactor);
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
                // If the current chold node is full, create a new sibling
                InternalNode<K, V> newSibling = new InternalNode<>(fanout);

                // First root node
                if (parent() == null) { // TODO: handle useless root
                    setParent(new InternalNode<>(fanout));
                    parent().add(this);
                }

                // and migrate the last written child node to this new sibling
                newSibling.add(childNode);
                parent().add(newSibling);
                setNext(newSibling);

                System.out.println("childNode = " + childNode.isLeaf());
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
    }

    public static class LeafNode<K, V> extends Node<K, V> implements Iterable<Pair<K, V>> {

        final K[] keys;
        final V[] vals;

        private LeafNode(Class<K> keyClass, Class<V> valueClass, int branchingFactor) {
            super(branchingFactor);
            keys = getArray(keyClass, branchingFactor);
            vals = getArray(valueClass, branchingFactor);
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
    }

    // TODO: do dynamic block sizing!
    // TODO: layout the nodes each one node per block; then the nodes are sequentially numbered and addressable
    // do overflow area for the nodes
    private static int BLOCK_SIZE = 4096;

    // TODO: optimise for the case of PointerNode, which has only one child

    public static class TreeWriter<K, V> {

//        LeafNodeSe<K, V> internalNodeSerializer;
        private final InternalNodeSerializer<K, V> internalNodeSerializer;
        private final LeafNodeSerializer<K, V> leafNodeSerializer;

        public TreeWriter(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
            this.leafNodeSerializer = new LeafNodeSerializer<K, V>(keySerializer, valueSerializer);
            this.internalNodeSerializer = new InternalNodeSerializer<K, V>(keySerializer, valueSerializer);
        }

        // TODO: switch to channel & something else?
        public void serialize(RandomAccessFile out, InternalNode<K, V> root) throws IOException {
            Node<K, V> firstInLevel = root;

            while (firstInLevel != null) {
                Iterator<Node<K, V>> siblings = firstInLevel.siblings();

                int childOffset = 0;
                while (siblings.hasNext()) {
                    Node<K, V> current = siblings.next();
                    System.out.println("current = " + current);

                    if (current.isLeaf()) {
                        LeafNode<K, V> cast = (LeafNode<K, V>) current;
                        leafNodeSerializer.serialize(out, cast);
                        out.writeLong(childOffset);
                    } else {
                        InternalNode<K, V> cast = (InternalNode<K, V>) current;
                        internalNodeSerializer.serialize(out, cast);
                        out.writeLong(childOffset);

                        // The beginning of the "node" specified by the child starts where the
                        for (Node<K, V> grandchild : cast) {
                            if (grandchild.isLeaf())
                                childOffset += leafNodeSerializer.sizeof((LeafNode<K, V>) grandchild) + Long.BYTES;
                            else
                                childOffset += internalNodeSerializer.sizeof((InternalNode<K, V>) grandchild) + Long.BYTES;
                        }
                    }

                    if (current.isLeaf()) {
                        leafNodeSerializer.serialize(out, (LeafNode<K, V>) current);
                    } else {
                        internalNodeSerializer.serialize(out, (InternalNode<K, V>) current);
                    }
                    align(out, BLOCK_SIZE);
                }

                if (firstInLevel.isLeaf())
                    break;
                else {
                    firstInLevel = ((InternalNode<K, V>) firstInLevel).iterator().next();
                }
            }
        }

        public static long align(long val, int boundary)
        {
            return (val + boundary) & ~(boundary - 1);
        }

        private void align(RandomAccessFile out, int blockSize) throws IOException {
            long endOfBlock = out.getFilePointer();
            if ((endOfBlock & (BLOCK_SIZE - 1)) != 0) // align on the block boundary if needed
                out.seek(align(out.getFilePointer(), blockSize));
        }
    }

    public class LeafSerialiser implements Serializer<LeafNode<K, V>> {
        public void serialize(DataOutput out, LeafNode leaf) {
            // out.write();
        }

        public int sizeof(LeafNode<K, V> pairs) {
            return 0;
        }
    }


    /**
     * (byte info)
     * <p>
     * Next two are only present on in the root node:
     * ( short16 min_key_length) ( min_key_length bytes min_key)
     * ( short16 max_key_length) ( min_key_length bytes max_key)
     * <p>
     * ( short16 childrean_count )
     * children_count * [ (short16 key_length) ]
     * children_count * [ (key_length bytes key) ]
     * // THAT IS PULLED UP (children_count + 1) * [ (int32 child_offset) ]
     * <p>
     * TODO: fixed fanout keys
     * TODO: calculate offset in blocks!
     */
    public static class InternalNodeSerializer<K, V> implements Serializer<InternalNode<K, V>> {

        private final Serializer<K> keySerializer;
        private final Serializer<V> valueSerializer;

        public InternalNodeSerializer(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
        }

        public void serialize(DataOutput out, InternalNode<K, V> node) throws IOException {
            out.writeByte(0);

            if (node.isRoot()) {
                keySerializer.serialize(out, node.minKey());
                keySerializer.serialize(out, node.maxKey());
            }

            out.write(node.count - 1);

            // TODO: get rid of so many iterations!

            int childOffset = 0;
            for (Node<K, V> child : butFirst(node)) {
                out.writeShort(childOffset);
                childOffset += keySerializer.sizeof(child.minKey());
            }

            for (Node<K, V> child : butFirst(node)) {
                keySerializer.serialize(out, child.minKey());
            }
        }

        public int sizeof(InternalNode<K, V> node) {
            int size = Byte.BYTES;
            if (node.isRoot()) {
                size += 2 * Short.BYTES;
            }

            size += Short.BYTES + (node.count - 1) * Short.BYTES;

            // TODO: fixed fanout keys!
            for (Node<K, V> child : butFirst(node)) {
                keySerializer.sizeof(child.minKey());
            }

            return size;
        }
    }

    public static class LeafNodeSerializer<K, V> implements  Serializer<LeafNode<K, V>> {

        private final Serializer<K> keySerializer;
        private final Serializer<V> valueSerializer;

        public LeafNodeSerializer(Serializer<K> keySerializer, Serializer<V> valueSerializer) {
            this.keySerializer = keySerializer;
            this.valueSerializer = valueSerializer;
        }

        /**
         * (short16 child_count)
         * child_count * [(long64 child_entry_offset) (short16 key_length)]
         * child_count * []
         */
        @Override
        public void serialize(DataOutput out, LeafNode<K, V> node) throws IOException {
            out.writeShort(node.count);

            int childOffset = 0;
            for (Pair<K, V> pair : node) {
                out.writeLong(childOffset);
                short keySize = (short) keySerializer.sizeof(pair.getKey());
                childOffset += keySize;
                out.writeShort(keySize);
                childOffset += valueSerializer.sizeof(pair.getValue());
            }


            for (Pair<K, V> pair : node) {
                keySerializer.serialize(out, pair.getKey());
                out.writeInt(valueSerializer.sizeof(pair.getValue()));
                valueSerializer.serialize(out, pair.getValue());
                childOffset += valueSerializer.sizeof(pair.getValue());
            }


        }

        @Override
        public int sizeof(LeafNode<K, V> node) {
            int size = Short.BYTES;

            size += node.count * (Long.BYTES + Short.BYTES);

            for (Pair<K, V> pair : node) {
                size += keySerializer.sizeof(pair.getKey()) + Integer.SIZE + valueSerializer.sizeof(pair.getValue());
            }

            return size;
        }
    }

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
package com.ifesdjeen.bbaum;

import javafx.util.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.System.out;

public class BTreeTest {

    //  RootNode[7, 13]
//  InternalNode[4]
//  LeafNode [1: 1, 2: 2, 3: 3], LeafNode [4: 4, 5: 5, 6: 6]
//  InternalNode[10]
//  LeafNode [7: 7, 8: 8, 9: 9], LeafNode [10: 10, 11: 11, 12: 12]
//  InternalNode[16]
//  LeafNode [13: 13, 14: 14, 15: 15], LeafNode [16: 16, 17: 17, 18: 18]
    @Test
    public void testBTree() {
        BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(INTEGER_SERIALIZER, STRING_SERIALIZER, 3);
        SortedMap<Integer, String> input = new TreeMap<>();
        for (int i = 1; i <= 500; i++) {
            input.put(i, Integer.toString(i));
        }


//RootNode[1, 28]
//	InteriorNode3 [1, 10, 19]
//		InteriorNode3 [1, 4, 7]
//			LeafNode3 [1: 1, 2: 2, 3: 3], LeafNode3 [4: 4, 5: 5, 6: 6], LeafNode3 [7: 7, 8: 8, 9: 9]
//		InteriorNode3 [10, 13, 16]
//			LeafNode3 [10: 10, 11: 11, 12: 12], LeafNode3 [13: 13, 14: 14, 15: 15], LeafNode3 [16: 16, 17: 17, 18: 18]
//		InteriorNode3 [19, 22, 25]
//			LeafNode3 [19: 19, 20: 20, 21: 21], LeafNode3 [22: 22, 23: 23, 24: 24], LeafNode3 [25: 25, 26: 26, 27: 27]
//
//	InteriorNode3 [28, 37, 46]
//		InteriorNode3 [28, 31, 34]
//			LeafNode3 [28: 28, 29: 29, 30: 30], LeafNode3 [31: 31, 32: 32, 33: 33], LeafNode3 [34: 34, 35: 35, 36: 36]
//		InteriorNode3 [37, 40, 43]
//			LeafNode3 [37: 37, 38: 38, 39: 39], LeafNode3 [40: 40, 41: 41, 42: 42], LeafNode3 [43: 43, 44: 44, 45: 45]
//		InteriorNode3 [46, 49]
//			LeafNode3 [46: 46, 47: 47, 48: 48], LeafNode3 [49: 49, 50: 50]
        btree.ingestSorted(input);


        out.println(btree.rightmostParent.root().toStringRecursive());
    }

    @Test
    public void testBTreeLeavesInSortedOrder() {
        BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(INTEGER_SERIALIZER, STRING_SERIALIZER, 3);
        SortedMap<Integer, String> input = new TreeMap<>();
        for (int i = 1; i <= 50; i++) {
            input.put(i, Integer.toString(i));
        }
        btree.ingestSorted(input);

        List<Pair<Integer, String>> contents = new ArrayList<>(50);

        depthFirst(btree.rightmostParent.root(), (Pair<Integer, String> i) -> contents.add(i));

        Iterator<Map.Entry<Integer, String>> iter = input.entrySet().iterator();
        for (Pair<Integer, String> content : contents) {
            Map.Entry<Integer, String> current = iter.next();
            Assert.assertEquals(current.getKey(), content.getKey());
            Assert.assertEquals(current.getValue(), content.getValue());
        }
    }

    String alphabet = "abcdefghijklmopqrtuvwxyz";

    String repeat(char c, int times) {
        StringBuilder bb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            bb.append(c);
        }
        return bb.toString();
    }

    @Test
    public void asd() throws Throwable {
        String file = "/tmp/test";
        new File(file).delete();
        BTreeBuilder<String, String> btree = new BTreeBuilder<>(STRING_SERIALIZER, STRING_SERIALIZER, 3);
        SortedMap<String, String> input = new TreeMap<>();
        for (int i = 1; i <= 100; i++) {
            input.put(repeat(alphabet.charAt(i % alphabet.length()), 3) + (i / alphabet.length()),
                repeat(alphabet.charAt(i % alphabet.length()), 5) + (i / alphabet.length())
            );
        }
        btree.ingestSorted(input);

        out.println(btree.rightmostParent.root().toStringRecursive());

        OutputStream os = Files.newOutputStream(new File(file).toPath(), StandardOpenOption.CREATE);
        btree.serializeTree(os);
        os.flush();
//        new BTreeBuilder.TreeWriter<String, String>(3, STRING_SERIALIZER, STRING_SERIALIZER).serialize(rf, btree.rightmostParent.root());

        os.close();

        BTree<String, String> tree = new BTree<String, String>(file, STRING_SERIALIZER, STRING_SERIALIZER);
        System.out.println("tree.nodesOnLevel(1) = " + tree.nodesOnLevel(1));
        System.out.println("tree.nodesOnLevel(2) = " + tree.nodesOnLevel(2));
        String s = ByteBufferUtil.prettyHexDump(tree.getNode(1, 0, 2));
        System.out.println(s);
        // TODO: test to validate tree integrity, down to the node level
    }


    // Depth is growing only logarithmically, so that should not be a problem
    private static <K extends Comparable, V> void depthFirst(BTreeBuilder.Node<K, V> node, Consumer<Pair<K, V>> callback) {
        node.ifLeaf((leaf) -> {
            for (Pair<K, V> kvPair : leaf) {
                callback.accept(kvPair);
            }
        })
            .ifInternal((interior) -> {
                for (BTreeBuilder.Node<K, V> child : interior) {
                    depthFirst(child, callback);
                }
            });
    }

    private static <K, V> BTreeBuilder.InternalNode<K, V> interiorNode(int fanout, BTreeBuilder.ValueSerializer<K> keySerializer, BTreeBuilder.ValueSerializer<V> valueSerializer) {
        return new BTreeBuilder.InternalNode<K, V>(keySerializer, valueSerializer, fanout);
    }

    private static <K, V> BTreeBuilder.LeafNode<K, V> leaf(int fanout, BTreeBuilder.ValueSerializer<K> keySerializer, BTreeBuilder.ValueSerializer<V> valueSerializer) {
        return BTreeBuilder.makeLeaf(keySerializer, valueSerializer, fanout);
    }

    private static BTreeBuilder.LeafNode<String, String> leaf(int fanout, String... kvps) {
        assert kvps.length % 2 == 0;
        BTreeBuilder.LeafNode<String, String> leafNode = leaf(fanout, STRING_SERIALIZER, STRING_SERIALIZER);
        for (int i = 0; i < kvps.length; i += 2) {
            leafNode.add(kvps[i], kvps[i + 1]);
        }
        return leafNode;

    }

    @Test
    public void internalNodeSerializationTest() throws IOException {
        final int FANOUT = 10;
        ByteBuffer byteBuffer = ByteBuffer.allocate(BTreeBuilder.BLOCK_SIZE);

        BTreeBuilder.InternalNode<String, String> node = interiorNode(FANOUT, STRING_SERIALIZER, STRING_SERIALIZER);
        for (int i = 1; i <= FANOUT; i++) {
            node.add(
                leaf(FANOUT,
                    repeat(alphabet.charAt(i % alphabet.length()), 3),
                    repeat(alphabet.charAt(i % alphabet.length()), 5)));
        }

        node.serialize(byteBuffer);
        byteBuffer.flip();
        byteBuffer.rewind();
        System.out.println("ByteBufferUtil.hexDump(byteBuffer) = " + ByteBufferUtil.prettyHexDump(byteBuffer));
        // TODO: calculate node byte size
    }

    @Test
    public void leafNodeSerializationTest() throws IOException {
        final int FANOUT = 10;
        ByteBuffer byteBuffer = ByteBuffer.allocate(BTreeBuilder.BLOCK_SIZE);

        String[] kvps = new String[FANOUT * 2];
        for (int i = 0; i < FANOUT * 2; i += 2) {
            kvps[i] = repeat(alphabet.charAt(i % alphabet.length()), 3);
            kvps[i + 1] = repeat(alphabet.charAt(i % alphabet.length()), 5);
        }
        BTreeBuilder.LeafNode<String, String> node = leaf(FANOUT, kvps);
        node.serialize(byteBuffer);
        byteBuffer.flip();
        byteBuffer.rewind();
        System.out.println("ByteBufferUtil.hexDump(byteBuffer) = " + ByteBufferUtil.prettyHexDump(byteBuffer));
        // TODO: calculate node byte size
    }

    private static final BTreeBuilder.ValueSerializer<String> STRING_SERIALIZER = new StringSerializer();
    private static final BTreeBuilder.ValueSerializer<Integer> INTEGER_SERIALIZER = new IntegerSerializer();
    public static class IntegerSerializer implements BTreeBuilder.ValueSerializer<Integer> {

        @Override
        public void serialize(ByteBuffer out, Integer v) throws IOException {
            out.putInt(v);
        }

        @Override
        public Integer deserialize(ByteBuffer out) throws IOException {
            return out.getInt();
        }

        @Override
        public int sizeof(Integer integer) {
            return Integer.BYTES;
        }
    }
    public static class StringSerializer implements BTreeBuilder.ValueSerializer<String> {

        @Override
        public void serialize(ByteBuffer out, String s) throws IOException {
            out.putInt(s.length());
            out.put(s.getBytes());
        }

        @Override
        public String deserialize(ByteBuffer out) throws IOException {
            int length = out.getInt();
            byte[] bytes = new byte[length];
            out.get(bytes);
            return new String(bytes);
        }

        @Override
        public int sizeof(String s) {
            return Integer.BYTES + s.length();
        }
    }

//  @Test
//  public void writeReadBTree() {
//    BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(Integer.class, String.class, 4, null);
//    SortedMap<Integer, String> input = new TreeMap<>();
//    for (int i = 1; i <= 20; i++) {
//      input.put(i, Integer.toString(i));
//    }
//
//    btree.ingestSorted(input);
//
//    // InternalNode ( 5, 9, null, )
//    // [LeafNode [1: 1, 2: 2, 3: 3, 4: 4], LeafNode [5: 5, 6: 6, 7: 7, 8: 8], LeafNode [9: 9, 10: 10, 11: 11, 12: 12], ]
//
//    // InternalNode ( null, null, null, )
//    // [InternalNode ( 5, 9, 13, ) [LeafNode [1: 1, 2: 2, 3: 3, 4: 4], LeafNode [5: 5, 6: 6, 7: 7, 8: 8], ],
//    //  InternalNode ( null, null, null, ) [LeafNode [9: 9, 10: 10, 11: 11, 12: 12], ], ]
//
//    System.out.println("btree.root = " + btree.root);
//  }


}


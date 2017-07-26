package com.ifesdjeen.bbaum;

import javafx.util.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

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
        BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(Integer.class, String.class, 3, null);
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


        System.out.println(btree.rightmostParent.root().toStringRecursive());
    }

    @Test
    public void testBTreeLeavesInSortedOrder() {
        BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(Integer.class, String.class, 3, null);
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
        new File("/tmp/test").delete();
        RandomAccessFile rf = new RandomAccessFile("/tmp/test", "rw");
        BTreeBuilder<String, String> btree = new BTreeBuilder<>(String.class, String.class, 3, null);
        SortedMap<String, String> input = new TreeMap<>();
        for (int i = 1; i <= 50; i++) {

            input.put(repeat(alphabet.charAt(i % alphabet.length()), 3),
                repeat(alphabet.charAt(i % alphabet.length()), 5));
        }
        btree.ingestSorted(input);

        System.out.println(btree.rightmostParent.root().toStringRecursive());

        new BTreeBuilder.TreeWriter<String, String>(new BTreeBuilder.Serializer<String>() {
            @Override
            public void serialize(DataOutput out, String s) throws IOException {
                out.writeInt(s.length());
                out.writeBytes(s);
            }

            @Override
            public int sizeof(String s) {
                return Integer.BYTES + s.length();
            }
        }, new BTreeBuilder.Serializer<String>() {
            @Override
            public void serialize(DataOutput out, String s) throws IOException {
                out.writeInt(s.length());
                out.writeBytes(s);
            }

            @Override
            public int sizeof(String s) {
                return Integer.BYTES + s.length();
            }
        }).serialize(rf, btree.rightmostParent.root());

        rf.close();
    }


    // Depth is growing only logarithmically, so that should not be a problem
    private static <K extends Comparable, V> void depthFirst(BTreeBuilder.Node<K, V> node, Consumer<Pair<K, V>> callback) {
        node.ifLeaf((leaf) -> {
            for (Pair<K, V> kvPair : leaf) {
                callback.accept(kvPair);
            }
        })
            .ifInterior((interior) -> {
                for (BTreeBuilder.Node<K, V> child : interior) {
                    depthFirst(child, callback);
                }
            });
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


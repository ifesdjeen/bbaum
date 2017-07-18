package com.ifesdjeen.bbaum;

import org.junit.Test;

import java.util.SortedMap;
import java.util.TreeMap;

public class BTreeTest {

//  RootNode[7, 13]
//  InteriorNode[4]
//  LeafNode [1: 1, 2: 2, 3: 3], LeafNode [4: 4, 5: 5, 6: 6]
//  InteriorNode[10]
//  LeafNode [7: 7, 8: 8, 9: 9], LeafNode [10: 10, 11: 11, 12: 12]
//  InteriorNode[16]
//  LeafNode [13: 13, 14: 14, 15: 15], LeafNode [16: 16, 17: 17, 18: 18]
  @Test
  public void testBTree() {
    BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(Integer.class, String.class, 3, null);
    SortedMap<Integer, String> input = new TreeMap<>();
    for (int i = 1; i <= 50; i++) {
      input.put(i, Integer.toString(i));
    }

    // InteriorNode ( 7, 13, )
    // [InteriorNode ( 4, ) [LeafNode [1: 1, 2: 2, 3: 3], LeafNode [4: 4, 5: 5, 6: 6], ],
    //  InteriorNode ( 10, ) [LeafNode [7: 7, 8: 8, 9: 9], LeafNode [10: 10, 11: 11, 12: 12], ],
    //  InteriorNode ( 16, 19, ) [LeafNode [13: 13, 14: 14, 15: 15], LeafNode [16: 16, 17: 17, 18: 18], LeafNode [19: 19, 20: 20, 21: 21], ], ]

    btree.ingestSorted(input);


    System.out.println(btree.rightmostParent.root().toStringRecursive());
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
//    // InteriorNode ( 5, 9, null, )
//    // [LeafNode [1: 1, 2: 2, 3: 3, 4: 4], LeafNode [5: 5, 6: 6, 7: 7, 8: 8], LeafNode [9: 9, 10: 10, 11: 11, 12: 12], ]
//
//    // InteriorNode ( null, null, null, )
//    // [InteriorNode ( 5, 9, 13, ) [LeafNode [1: 1, 2: 2, 3: 3, 4: 4], LeafNode [5: 5, 6: 6, 7: 7, 8: 8], ],
//    //  InteriorNode ( null, null, null, ) [LeafNode [9: 9, 10: 10, 11: 11, 12: 12], ], ]
//
//    System.out.println("btree.root = " + btree.root);
//  }


}



package com.ifesdjeen.bbaum;

import org.junit.Test;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

public class BTreeTest {

  @Test
  public void testBTree() {

    BTreeBuilder<Integer, String> btree = new BTreeBuilder<>(5, null);
    SortedMap<Integer, String> input = new TreeMap<>();
    for (int i = 0; i < 10; i++) {
      input.put(i, Integer.toString(i));
    }

    btree.ingestSorted(input);

    System.out.println("btree.root = " + btree.root);
  }
}



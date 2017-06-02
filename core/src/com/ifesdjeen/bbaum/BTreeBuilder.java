package com.ifesdjeen.bbaum;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;

// TODO: fixed term size optimisation
// TODO: create from both sorted and unsorted data
// TODO: query modes?
public class BTreeBuilder<K extends Comparable, V> {

  private final int branchingFactor;
  private final Function<K, ByteBuffer> converter;

  public BTreeBuilder(int branchingFactor, Function<K, ByteBuffer> converter) {
    this.branchingFactor = branchingFactor;
    this.converter = converter;
  }

  public Node<K, V> root;
  LeafNode currentLeaf;
  InteriorNode rightmostParent;

  public void ingestSorted(SortedMap<K, V> source) {
    // Create the root node
    currentLeaf = new LeafNode();
    rightmostParent = null;
    root = root;

    for (Map.Entry<K, V> kvEntry : source.entrySet()) {
      currentLeaf.add(kvEntry.getKey(), kvEntry.getValue());
    }
  }

  // TODO: memory size
  private static int memorySize() {
    return 0;
  }

  public abstract class Node<K, V> {
    final Node<K, V>[] children;

    int count;
    int size;


    public Node() {
      children = (Node<K, V>[]) Array.newInstance(Node.class, branchingFactor);

      this.count = 0;
      this.size = branchingFactor;
    }

    public abstract void split();

    public boolean isAlmostFull() {
      return count == (size - 1);
    }
  }

  public class InteriorNode extends Node<K, V> {

    Node<K, V> parent;
    final K[] keys;
    final Node[] nodes;

    public InteriorNode() {
      keys = (K[]) Array.newInstance(Object.class, branchingFactor);
      nodes = (Node[]) Array.newInstance(Node.class, branchingFactor);
    }

    public void split() {
      InteriorNode newNode = new InteriorNode();

      int splitPoint = size - 1; // or -2? SASI has -2
      for (int i = splitPoint; i < count; i++) {

      }

      // Nullify the original node pointers
      for (int i = count; i >= splitPoint; i--) {

      }
    }

    public void add(Node leaf) {


    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("InteriorNode [");
      for (K key : keys) {
        builder.append(key).append(", ");
      }
      builder.append("]");
      return builder.toString();
    }
  }

  public <T> T[] getArray(int size) {
    T[] arr = (T[])new Object[size];
    return arr;
  }

  public class LeafNode extends Node<K, V> {

    final Object[] keys;
    final Object[] vals;

    public LeafNode() {
      ArrayList
      keys = getArray(branchingFactor);
      vals = getArray(branchingFactor);
    }

    @Override
    public void split() {
      LeafNode newNode = new LeafNode();

      int splitPoint = size - 1; // or -2? SASI has -2
      for (int i = splitPoint; i < count; i++) {
        newNode.keys[i] = keys[i];
        keys[i] = null;
        newNode.vals[i] = vals[i];
        vals[i] = null;
      }

      newNode.count = 1;

      currentLeaf = newNode;
      rightmostParent.add(newNode);

      System.out.println("newNode = " + newNode);
    }

    public void add(K k, V v) {
      if (!isAlmostFull()) {
        keys[count] = k;
        vals[count] = v;
        count++;
      } else {
        split();
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("LeafNode [");

      for (int i = 0; i < count; i++) {
        builder.append(keys[i]).append(": ").append(vals[i]);
        if (i != (count - 1))
          builder.append(", ");
      }

      builder.append("]");
      return builder.toString();
    }
  }

  public static class Pair<L, R> {
    public final L left;
    public final R right;

    Pair(L left, R right) {
      this.left = left;
      this.right = right;
    }
  }

  public static <K, V> Pair<K, V> create(K k, V v) {
    return new Pair<>(k, v);
  }
}

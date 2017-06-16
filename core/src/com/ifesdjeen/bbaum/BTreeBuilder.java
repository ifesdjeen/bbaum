package com.ifesdjeen.bbaum;

import java.io.DataOutput;
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
  private final Class<K> keyClass;
  private final Class<V> valueClass;

  public BTreeBuilder(Class<K> keyClass, Class<V> valueClass, int branchingFactor, Function<K, ByteBuffer> converter) {
    this.branchingFactor = branchingFactor;
    this.converter = converter;
    this.keyClass = keyClass;
    this.valueClass = valueClass;
  }

  InteriorNode root;
  LeafNode currentLeaf;
  InteriorNode rightmostParent;

  public void ingestSorted(SortedMap<K, V> source) {
    // Create the root node
    currentLeaf = new LeafNode();
    rightmostParent = new InteriorNode();
    root = rightmostParent;

    for (Map.Entry<K, V> kvEntry : source.entrySet()) {
      currentLeaf.add(kvEntry.getKey(), kvEntry.getValue());

      if (currentLeaf.isFull())
      {
        rightmostParent.add(currentLeaf);
        currentLeaf = new LeafNode();
      }
    }

    if (!currentLeaf.isEmpty())
      rightmostParent.add(currentLeaf);
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
      children = (Node[]) Array.newInstance(Node.class, branchingFactor);

      this.count = 0;
      this.size = branchingFactor;
    }

    public boolean isFull() {
      return count == size;
    }

    public boolean isEmpty() {
      return count == 0;
    }

    public abstract K minKey();
    public abstract boolean isLeaf();
    public abstract String preToString();
    public abstract String postToString();
  }

  private class InteriorNode<K, V> extends Node<K, V> {

    InteriorNode parent;
    final Node<K, V>[] nodes;

    public InteriorNode() {
      nodes = getArray(Node.class, branchingFactor);
    }

    @Override
    public K minKey() {
      assert !isEmpty() : "Empty node has no min key";
      return nodes[0].minKey();
    }

    @Override
    public boolean isLeaf() {
      return false;
    }

    public void add(Node<K, V> childNode) {
      if (isFull()) {
        InteriorNode<K, V> newNode = new InteriorNode<K, V>();

        // First root node
        if (rightmostParent.parent == null) {
          System.out.println("first root");
          root = new InteriorNode<K, V>();
          root.add(rightmostParent);
          rightmostParent.parent = root;
        }
        newNode.parent = root;
        newNode.add(childNode);
        rightmostParent.parent.add(newNode);
        rightmostParent = newNode;
      }
      else {
//        if (count > 0) {
//          System.out.println("childNode.minKey() = " + childNode.minKey());
//          keys[count - 1] = childNode.minKey();
//        }
        nodes[count++] = childNode;
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("InteriorNode ");

      builder.append("( ");
      for (int i = 1; i < nodes.length; i++) {
        if (nodes[i] != null)
          builder.append(nodes[i].minKey()).append(", "); // TODO: trailing comma
      }
      builder.append(") ");

      builder.append("[");
      for (int i = 0; i < count; i++) {
        builder.append(nodes[i]).append(", "); // TODO: trailing comma
      }
      builder.append("]");
      return builder.toString();
    }

    public String preToString() {
      StringBuilder builder = new StringBuilder(parent == null ? "RootNode [" : "InteriorNode [");
      for (int i = 0; i < nodes.length; i++) {
        builder.append(nodes[i].minKey()).append(", "); // TODO: trailing comma
      }

      builder.append("]");
      return builder.toString();
    }

    public String postToString() {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < count; i++) {
        builder.append(nodes[i].preToString()).append(" "); // TODO: trailing comma
      }

      for (int i = 0; i < count; i++) {
        builder.append(nodes[i].postToString()).append(" "); // TODO: trailing comma
      }

      return builder.toString();
    }
  }

  public static <T> T[] getArray(Class<T> klass, int size) {
    return (T[]) Array.newInstance(klass, size);
  }

  private class LeafNode<K, V> extends Node<K, V> {

    final K[] keys;
    final V[] vals;

    public LeafNode() {
      keys = (K[])getArray(keyClass, branchingFactor);
      vals = (V[])getArray(valueClass, branchingFactor);
    }

    public K minKey() {
      assert !isEmpty() : "Empty leaf has no empty key";
      return keys[0];
    }

    @Override
    public boolean isLeaf() {
      return true;
    }

    public String preToString() {
      return toString();
    }

    public String postToString() {
      return toString();
    }

    public void add(K k, V v) {
      assert !isFull() : "Cannot insert into the full node";
      keys[count] = k;
      vals[count] = v;
      count++;
    }

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

  /**
   * Leaves are serialized
   */
  public class LeafSerialiser {
    public void serialize(DataOutput out, LeafNode leaf) {
      // out.write();
    }

    public int serializedSize() {
      return 0;
    }
  }
}

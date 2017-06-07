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
  private final Class<K> keyKlass;
  private final Class<V> valueClass;

  public BTreeBuilder(Class<K> keyKlass, Class<V> valueClass, int branchingFactor, Function<K, ByteBuffer> converter) {
    this.branchingFactor = branchingFactor;
    this.converter = converter;
    this.keyKlass = keyKlass;
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
      children = (Node<K, V>[]) Array.newInstance(Node.class, branchingFactor);

      this.count = 0;
      this.size = branchingFactor;
    }

    public abstract void split();

    public boolean isFull() {
      return count == size;
    }

    public boolean isEmpty() {
      return count == 0;
    }

    public abstract K minKey();

    public abstract String preToString();
    public abstract String postToString();
  }

  public class InteriorNode extends Node<K, V> {

    InteriorNode parent;
    final K[] keys; // denormalize?
    final Node[] nodes;

    public InteriorNode() {
      keys = getArray(keyKlass, branchingFactor - 1);
      nodes = getArray(Node.class, branchingFactor);
    }

    public void split() {
      InteriorNode newNode = new InteriorNode();

      int splitPoint = size - 2; // or -2? SASI has -2
      for (int i = splitPoint, j = 0; i < count; i++, j++) {
        newNode.nodes[j] = nodes[i];
        nodes[i] = null;
      }

      for (int i = 1; i < newNode.count; i++) {
        newNode.keys[i - 1] = (K) newNode.nodes[i].minKey();
      }
      count = splitPoint;

      newNode.count = 1;
      rightmostParent = newNode;
      if (parent == null) {
        root = new InteriorNode();
        this.parent = root;
        newNode.parent = root;
        parent.add(this);
      }
      else {
        newNode.parent = parent;
      }
      parent.add(newNode);
    }

    @Override
    public K minKey() {
      assert !isEmpty() : "Empty node has no min key";
      return keys[0];
    }

    public void add(Node leaf) {
      if (count > 0)
        // TODO seems we still do need to generify at least the node class, or?
        keys[count - 1] = (K) leaf.minKey();
      nodes[count++] = leaf;

      if (isFull()) {
        split();
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("InteriorNode ");

      builder.append("( ");
      for (int i = 0; i < keys.length; i++) {
        builder.append(keys[i]).append(", "); // todo: trailing comma
      }
      builder.append(") ");

      builder.append("[");
      for (int i = 0; i < count; i++) {
        builder.append(nodes[i]).append(", ");
      }
      builder.append("]");
      return builder.toString();
    }

    public String preToString() {
      StringBuilder builder = new StringBuilder(parent == null ? "RootNode [" : "InteriorNode [");
      for (int i = 0; i < keys.length; i++) {
        builder.append(keys[i]).append(", "); // todo: trailing comma
      }

      builder.append("]");
      return builder.toString();
    }

    public String postToString() {
      StringBuilder builder = new StringBuilder();

      for (int i = 0; i < count; i++) {
        builder.append(nodes[i].preToString()).append(" ");
      }

      for (int i = 0; i < count; i++) {
        builder.append(nodes[i].postToString()).append(" ");
      }

      return builder.toString();
    }
  }

  public static <T> T[] getArray(Class<T> klass, int size) {
    return (T[]) Array.newInstance(klass, size);
  }

  public class LeafNode extends Node<K, V> {

    final K[] keys;
    final V[] vals;

    public LeafNode() {
      keys = getArray(keyKlass, branchingFactor);
      vals = getArray(valueClass, branchingFactor);
    }

    @Override
    public void split() {
      LeafNode newNode = new LeafNode();

      int splitPoint = size - 2; // or -2? SASI has -2
      // todo: System#arrayCopy?
      for (int i = splitPoint, j = 0; i < count; i++, j++) {
        newNode.keys[j] = keys[i];
        keys[i] = null;
        newNode.vals[j] = vals[i];
        vals[i] = null;
      }
      count = splitPoint;

      newNode.count = 1;

      currentLeaf = newNode;
      rightmostParent.add(newNode);
    }

    @Override
    public K minKey() {
      assert !isEmpty() : "Empty leaf has no empty key";
      return keys[0];
    }

    @Override
    public String preToString() {
      return toString();
    }

    @Override
    public String postToString() {
      return toString();
    }

    public void add(K k, V v) {
      assert !isFull() : "Cannot insert into the full node";
      keys[count] = k;
      vals[count] = v;
      count++;
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

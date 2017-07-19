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

  LeafNode<K, V> currentLeaf;
  InteriorNode<K, V> rightmostParent;

  public void ingestSorted(SortedMap<K, V> source) {
    // Create the root node
    currentLeaf = new LeafNode<>();
    rightmostParent = new InteriorNode<>();

    for (Map.Entry<K, V> kvEntry : source.entrySet()) {
      currentLeaf.add(kvEntry.getKey(), kvEntry.getValue());

      if (currentLeaf.isFull())
      {
        rightmostParent.add(currentLeaf).ifPresent(nodes -> rightmostParent = nodes);
        currentLeaf = new LeafNode<>();
      }
    }

    if (!currentLeaf.isEmpty())
      rightmostParent.add(currentLeaf);
  }

  // TODO: memory size
  private static int memorySize() {
    return 0;
  }

  public abstract class Node<K, V> implements Iterable<Node<K, V>> {
    final Node<K, V>[] children;

    private InteriorNode<K, V> parent;

    int count;
    int size;
    public Node() {
      children = (Node<K, V>[]) Array.newInstance(Node.class, branchingFactor);

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

    public InteriorNode<K, V> parent() {
      return parent;
    }

    public InteriorNode<K, V> root() {
      if (parent == null)
        return (InteriorNode<K, V>) this; // TODO: move down
      else
        return parent.root();
    }

    public void setParent(InteriorNode<K, V> parent) {
      this.parent = parent;
    }

    public abstract Iterator<Node<K, V>> iterator();

    public abstract String toStringRecursive();
  }

  public class InteriorNode<K, V> extends Node<K, V> {

    private final Node<K, V>[] nodes;

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

    /**
     * Returns the new rightmost parent if the previous one was full
     */
    public Optional<InteriorNode<K, V>> add(Node<K, V> childNode) {
      if (isFull()) {
        // If the current chold node is full, create a new sibling
        InteriorNode<K, V> newSibling = new InteriorNode<>();

        // First root node
        if (parent() == null) { // TODO: handle useless root
          setParent(new InteriorNode<K, V>());
          parent().add(this);
        }

        // and migrate the last written child node to this new sibling
        newSibling.add(childNode);
        parent().add(newSibling);
        System.out.println("childNode = " + childNode.isLeaf());
        // new rightmost parent will always be created on the rightmost
        // edge, which is closest to the leaves
        if (childNode.isLeaf())
          return Optional.of(newSibling);
      }
      else {
        nodes[count++] = childNode;
        childNode.setParent(this);
      }

      return Optional.empty();
    }

    public Iterator<Node<K, V>> iterator() {

      return new Iterator<Node<K, V>>() {
        final int length = children.length;
        int i = 0;

        @Override
        public boolean hasNext() {
          return i < length;
        }

        @Override
        public Node<K, V> next() {
          return children[i++];
        }
      };
    }

    public String toString()
    {
      StringBuilder builder = new StringBuilder();
      if (parent() == null)
        builder.append("RootNode");
      else
        builder.append("InteriorNode");

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
      if (parent() == null)
      {
        builder.append("RootNode");
        depth = 0;
      }
      else
      {
        depth = 1;
        Node parent = parent();
        while (parent != null)
        {
          builder.append("\t");
          parent = parent.parent();
          depth++;
        }
        builder.append("InteriorNode").append(size).append(' ');
      }


      builder.append("[");
      for (int i = 0; i < count; i++) {
        if (nodes[i] !=null) //tood: remove
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

    @Override
    public Iterator<Node<K, V>> iterator() {
      return Iterators.empty();
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

    public String toString(){
      StringBuilder builder = new StringBuilder("LeafNode");
      builder.append(size).append(" [");

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

  // Serialise as much as fits into the block of 4K bytes.
  // One of the ways to lay things out is to first lay out the nodes (knowing how much will fit)
  // since each node is predictably-ordered, we can form the offset table in the end, which
  // will be loaded into the memory for further traversal

  // Another way to lay them out would be to pre-calculate the offsets upfront: for example,
  // by saying
  //
  public class LeafSerialiser {
    public void serialize(DataOutput out, LeafNode leaf) {
      // out.write();
    }

    public int serializedSize() {
      return 0;
    }
  }
}

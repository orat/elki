package de.lmu.ifi.dbs.index;

import de.lmu.ifi.dbs.persistent.Page;

import java.util.Enumeration;

/**
 * This interface defines the common requirements of nodes in an index structure.
 * A node has to extend the page interface for persistent storage and
 * has to provide an enumeration over its children.
 *
 * @author Elke Achtert (<a
 *         href="mailto:achtert@dbs.ifi.lmu.de">achtert@dbs.ifi.lmu.de</a>)
 * @see de.lmu.ifi.dbs.persistent.Page
 */
public interface Node<E extends Entry> extends Page {
  /**
   * Returns an enumeration of the children paths of this node.
   *
   * @param parentPath the path to this node
   * @return an enumeration of the children paths of this node
   */
  Enumeration<IndexPath<E>> children(IndexPath<E> parentPath);

  /**
   * Returns the number of entries of this node.
   *
   * @return the number of entries of this node
   */
  int getNumEntries();

  /**
   * Returns true if this node is a leaf node, false otherwise.
   *
   * @return true if this node is a leaf node, false otherwise
   */
  boolean isLeaf();

  /**
   * Returns the entry at the specified index.
   *
   * @param index the index of the entry to be returned
   * @return the entry at the specified index
   */
  E getEntry(int index);
}

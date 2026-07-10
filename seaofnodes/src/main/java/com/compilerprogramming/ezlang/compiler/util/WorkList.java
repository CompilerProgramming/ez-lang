package com.compilerprogramming.ezlang.compiler.util;

import com.compilerprogramming.ezlang.compiler.node.Node;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;

/** A randomized worklist with fast duplicate suppression using node IDs. */
public class WorkList<E extends Node> {
    private Node[] _es = new Node[1];
    private int _len;
    private final BitSet _on = new BitSet();
    private final Random _random = new Random(123);

    public E push(E node) {
        if (node == null) return null;
        if (!_on.get(node._nid)) {
            _on.set(node._nid);
            if (_len == _es.length)
                _es = Arrays.copyOf(_es, _len << 1);
            _es[_len++] = node;
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    public E pop() {
        if (_len == 0) return null;
        int idx = _random.nextInt(_len);
        E node = (E)_es[idx];
        _es[idx] = _es[--_len];
        _on.clear(node._nid);
        return node;
    }
}
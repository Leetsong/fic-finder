package com.example.ficfinder.utils;

import java.util.*;

public class MultiTree<D> {

    //
    // TODO complete it
    //

    public static class Node<D> {

        private D data = null;
        private Node<D> parent = null;
        private List<Node<D>> children = new ArrayList<>();

        public Node() { }

        public Node(D data) {
            this.data = data;
        }

        public D getData() {
            return data;
        }

        public void setData(D data) {
            this.data = data;
        }

        public Node<D> getParent() {
            return parent;
        }

        public void setParent(Node<D> parent) {
            this.parent = parent;
        }

        public List<Node<D>> getChildren() {
            return children;
        }

        public void setChildren(List<Node<D>> children) {
            for (Node<D> c : children) {
                c.parent = this;
            }

            this.children = children;
        }

        public void addChild(Node<D> c) {
            c.parent = this;
            this.children.add(c);
        }

    }

    protected Node<D> root;

    public MultiTree() { }

    public MultiTree(Node<D> root) {
        this.root = root;
    }

    public Node<D> getRoot() {
        return root;
    }

    public void setRoot(Node<D> root) {
        this.root = root;
    }

    public boolean isEmpty() {
        return null == this.root;
    }

    public int size() {
        if (this.isEmpty()) {
            return 0;
        }

        int size = 0;
        Queue<Node<D>> queue = new LinkedList<>();

        queue.offer(this.root);
        while (!queue.isEmpty()) {
            Node<D> n = queue.poll();

            size ++;

            n.getChildren().forEach(c -> queue.offer(c));
        }

        return size;
    }

    public Node<D> find(D data) {
        if (this.isEmpty()) {
            return null;
        }

        Node<D> ret = null;
        Queue<Node<D>> queue = new LinkedList<>();

        queue.offer(this.root);
        while (!queue.isEmpty()) {
            Node<D> n = queue.poll();

            if (n.getData().equals(data)) {
                ret = n;
                break;
            }

            n.getChildren().forEach(c -> queue.offer(c));
        }

        return ret;
    }

    public boolean remove(D data) {
        Node<D> n = find(data);
        if (null == n) { return true; }

        if (n.equals(this.root)) {
            this.setRoot(null);
        } else {
            n.getParent().getChildren().remove(n);
        }

        return true;
    }

}
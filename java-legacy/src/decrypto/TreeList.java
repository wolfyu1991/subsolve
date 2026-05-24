/*
 * Decompiled with CFR 0.152.
 */
package decrypto;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import javax.swing.JPanel;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class TreeList<K> {
    Node<K> root = null;
    Comparator<K> cmp;
    int animateDelay = 0;
    ArrayList<Widget> widgets = new ArrayList();

    public TreeList(Comparator<K> cmp) {
        this.cmp = cmp;
    }

    public void reset() {
        this.root = null;
        this.animateStep();
    }

    public K first() {
        return this.elementAt(0);
    }

    public K last() {
        return this.elementAt(this.size() - 1);
    }

    static final int abs(int a) {
        return a > 0 ? a : -a;
    }

    static final int max(int a, int b) {
        return a > b ? a : b;
    }

    protected void rotateLL(Node<K> n) {
        Node m = n.left;
        Node T1 = m.left;
        Node T2 = m.right;
        Node T3 = n.right;
        assert (T1 != null && T2 != null && T3 != null);
        n.left = T1;
        n.right = m;
        m.left = T2;
        m.right = T3;
        T1.parent = n;
        T2.parent = m;
        T3.parent = m;
        m.parent = n;
        m.obj = null;
        m.val = T3.min;
        m.min = T2.min;
        n.obj = null;
        n.val = m.min;
        n.min = T1.min;
        m.sz = T1.sz + T2.sz;
        n.sz = m.sz + T3.sz;
        this.fixHeights(m);
        this.animateStep();
    }

    protected void rotateRR(Node<K> n) {
        Node m = n.right;
        Node T1 = n.left;
        Node T2 = m.left;
        Node T3 = m.right;
        assert (T1 != null && T2 != null && T3 != null);
        n.right = T3;
        n.left = m;
        m.left = T1;
        m.right = T2;
        m.parent = n;
        T1.parent = m;
        T2.parent = m;
        T3.parent = n;
        m.val = T2.min;
        m.obj = null;
        m.min = T1.min;
        n.val = T3.min;
        n.obj = null;
        n.min = m.min;
        m.sz = T2.sz + T3.sz;
        n.sz = T1.sz + m.sz;
        this.fixHeights(m);
        this.animateStep();
    }

    protected void balance(Node<K> n) {
        if (n == null) {
            return;
        }
        if (n.obj != null) {
            this.balance(n.parent);
            return;
        }
        if (TreeList.abs(n.left.height - n.right.height) <= 1) {
            this.balance(n.parent);
            return;
        }
        if (n.right.height > n.left.height) {
            if (n.right.left.height > n.right.right.height) {
                this.rotateLL(n.right);
                this.rotateRR(n);
            } else {
                this.rotateRR(n);
            }
        }
        if (n.left.height > n.right.height) {
            if (n.left.left.height < n.left.right.height) {
                this.rotateRR(n.left);
                this.rotateLL(n);
            } else {
                this.rotateLL(n);
            }
        }
        this.balance(n.parent);
    }

    public boolean contains(K k) {
        Node<K> n = this.findNode(k);
        if (n == null) {
            return false;
        }
        return this.cmp.compare(k, n.obj) == 0;
    }

    Node<K> findNode(K k) {
        Node<K> n = this.root;
        while (n != null && n.obj == null) {
            int c = this.cmp.compare(k, n.val);
            if (c < 0) {
                n = n.left;
                continue;
            }
            n = n.right;
        }
        return n;
    }

    Node<K> findNodeByIndex(int idx) {
        Node<K> n = this.root;
        while (n.obj == null) {
            if (idx >= n.left.sz) {
                idx -= n.left.sz;
                n = n.right;
                continue;
            }
            n = n.left;
        }
        return n;
    }

    public K elementAt(int idx) {
        return this.findNodeByIndex((int)idx).obj;
    }

    public int size() {
        if (this.root == null) {
            return 0;
        }
        return this.root.sz;
    }

    public void remove(K k) {
        Node<K> n = this.findNode(k);
        this.removeNode(n);
    }

    public void remove(int idx) {
        Node<K> n = this.findNodeByIndex(idx);
        this.removeNode(n);
    }

    void removeNode(Node<K> n) {
        Node p = n.parent;
        if (p == null) {
            this.root = null;
            return;
        }
        Node s = p.left == n ? p.right : p.left;
        Node gp = p.parent;
        if (gp.left == p) {
            gp.left = s;
        } else {
            gp.right = s;
        }
        s.parent = gp;
        this.fixHeights(s);
        this.animateStep();
        this.balance(s);
    }

    public boolean add(K obj) {
        Node<K> n = this.root;
        if (n == null) {
            this.root = new Node<K>(obj);
            return true;
        }
        while (n.obj == null) {
            if (this.cmp.compare(obj, n.val) < 0) {
                n = n.left;
                continue;
            }
            n = n.right;
        }
        int c = this.cmp.compare(obj, n.obj);
        if (c == 0) {
            return false;
        }
        if (c < 0) {
            n.left = new Node<K>(obj);
            n.right = new Node(n.obj);
            n.obj = null;
            n.val = obj;
            n.min = obj;
        } else {
            n.left = new Node(n.obj);
            n.right = new Node<K>(obj);
            n.obj = null;
            n.val = n.val;
            n.min = n.val;
        }
        n.height = 1;
        n.sz = 2;
        n.left.parent = n;
        n.right.parent = n;
        this.fixHeights(n);
        this.balance(n);
        this.animateStep();
        return true;
    }

    void fixHeights(Node<K> n) {
        while (n != null) {
            if (n.obj == null) {
                int newheight;
                n.height = newheight = TreeList.max(n.left.height + 1, n.right.height + 1);
                n.sz = n.left.sz + n.right.sz;
                n.val = n.right.min;
                n.min = n.left.min;
            }
            n = n.parent;
        }
    }

    void animateStep() {
        for (Widget w : this.widgets) {
            w.repaint();
        }
        if (this.widgets.size() > 0) {
            try {
                Thread.sleep(this.animateDelay);
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        }
    }

    public JPanel getWidget() {
        return new Widget();
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    class Widget
    extends JPanel {
        public int rankHeight;
        public static final long serialVersionUID = 1001L;

        public Widget() {
            TreeList.this.widgets.add(this);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(Color.white);
            g.fillRect(0, 0, this.getWidth(), this.getHeight());
            g.setColor(Color.black);
            if (TreeList.this.root == null) {
                return;
            }
            int width = this.getWidth();
            this.rankHeight = this.getHeight() / (TreeList.this.root.height + 2);
            this.paintRecurse((Graphics2D)g, TreeList.this.root, width / 2, 10, width / 2, 0);
        }

        void paintRecurse(Graphics2D g, Node<K> node, int x, int y, int width, int depth) {
            if (node.obj != null) {
                this.paintString(g, node.obj.toString(), x, y);
                return;
            }
            this.paintString(g, node.val.toString(), x, y);
            g.drawLine(x, y, x - width / 2, y + this.rankHeight);
            g.drawLine(x, y, x + width / 2, y + this.rankHeight);
            this.paintRecurse(g, node.left, x - width / 2, y + this.rankHeight, width / 2, depth + 1);
            this.paintRecurse(g, node.right, x + width / 2, y + this.rankHeight, width / 2, depth + 1);
        }

        void paintString(Graphics2D g, String s, int x, int y) {
            FontMetrics fm = g.getFontMetrics();
            Rectangle2D r = fm.getStringBounds(s, g);
            g.drawString(s, (int)((double)x - r.getWidth() / 2.0), (int)((double)y + r.getHeight()));
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    static class Node<K> {
        K val;
        K min;
        K obj;
        int height;
        int sz;
        Node<K> left;
        Node<K> right;
        Node<K> parent;

        Node(K obj) {
            this.obj = obj;
            this.val = obj;
            this.min = obj;
            this.sz = 1;
        }
    }
}


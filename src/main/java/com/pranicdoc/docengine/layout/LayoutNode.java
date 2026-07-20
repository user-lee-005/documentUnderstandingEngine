package com.pranicdoc.docengine.layout;

import com.pranicdoc.docengine.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One node of the Page -> Column -> Section -> Row -> Block -> Field -> Value tree.
 * Attributes carry stage-specific payloads (e.g. the raw primitives a detector should scan)
 * without forcing every stage to agree on a single rigid schema up front.
 */
public final class LayoutNode {

    private final String id;
    private final LayoutNodeType type;
    private final BoundingBox box;
    private final int page;
    private final List<LayoutNode> children = new ArrayList<>();
    private final Map<String, Object> attributes = new HashMap<>();

    public LayoutNode(String id, LayoutNodeType type, BoundingBox box, int page) {
        this.id = id;
        this.type = type;
        this.box = box;
        this.page = page;
    }

    public String id() {
        return id;
    }

    public LayoutNodeType type() {
        return type;
    }

    public BoundingBox box() {
        return box;
    }

    public int page() {
        return page;
    }

    public void addChild(LayoutNode child) {
        children.add(child);
    }

    public List<LayoutNode> children() {
        return List.copyOf(children);
    }

    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** Self and all descendants matching the predicate, depth-first. */
    public List<LayoutNode> collect(Predicate<LayoutNode> predicate) {
        List<LayoutNode> result = new ArrayList<>();
        collectInto(predicate, result);
        return result;
    }

    private void collectInto(Predicate<LayoutNode> predicate, List<LayoutNode> result) {
        if (predicate.test(this)) {
            result.add(this);
        }
        for (LayoutNode child : children) {
            child.collectInto(predicate, result);
        }
    }
}

/*
 * Copyright 2020 C. Schanck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sfj;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for drawing dotty/graphviz graphs. Tremendous overview here:
 * https://ncona.com/2020/06/create-diagrams-with-code-using-graphviz/
 * on a good working set of things to do.
 * <p>This is a work in progress, there is a lot more that can be done than
 * the API here supports, but there is enough to use it for its intended purpose:
 * drawing visuals of data structures.
 * <p>Excellent online viewer for dotty files here:
 * https://dreampuf.github.io/GraphvizOnline/
 */
public class DrawDot {
  final static String INDENTION = "  ";

  public interface Color {
    String colorName();
  }

  public enum ColorsSVG implements Color {
    aliceblue, antiquewhite, aqua, aquamarine, azure, beige, bisque, black, blanchedalmond, blue, blueviolet, brown, burlywood,
    cadetblue, chartreuse, chocolate, coral, cornflowerblue, cornsilk, crimson, cyan, darkblue, darkcyan, darkgoldenrod, darkgray,
    darkgreen, darkgrey, darkkhaki, darkmagenta, darkolivegreen, darkorange, darkorchid, darkred, darksalmon, darkseagreen,
    darkslateblue, darkslategray, darkslategrey, darkturquoise, darkviolet, deeppink, deepskyblue, dimgray, dimgrey, dodgerblue,
    firebrick, floralwhite, forestgreen, fuchsia, gainsboro, ghostwhite, gold, goldenrod, gray, grey, green, greenyellow, honeydew,
    hotpink, indianred, indigo, ivory, khaki, lavender, lavenderblush, lawngreen, lemonchiffon, lightblue, lightcoral, lightcyan,
    lightgoldenrodyellow, lightgray, lightgreen, lightgrey, lightpink, lightsalmon, lightseagreen, lightskyblue, lightslategray,
    lightslategrey, lightsteelblue, lightyellow, lime, limegreen, linen, magenta, maroon, mediumaquamarine, mediumblue,
    mediumorchid, mediumpurple, mediumseagreen, mediumslateblue, mediumspringgreen, mediumturquoise, mediumvioletred, midnightblue,
    mintcream, mistyrose, moccasin, navajowhite, navy, oldlace, olive, olivedrab, orange, orangered, orchid, palegoldenrod,
    palegreen, paleturquoise, palevioletred, papayawhip, peachpuff, peru, pink, plum, powderblue, purple, red, rosybrown, royalblue,
    saddlebrown, salmon, sandybrown, seagreen, seashell, sienna, silver, skyblue, slateblue, slategray, slategrey, snow,
    springgreen, steelblue, tan, teal, thistle, tomato, turquoise, violet, wheat, white, whitesmoke, yellow, yellowgreen;

    @Override
    public String colorName() {
      return name().toLowerCase();
    }
  }

  public enum ColorsX11 implements Color {
    aliceblue, antiquewhite, antiquewhite1, antiquewhite2, antiquewhite3, antiquewhite4, aquamarine, aquamarine1, aquamarine2,
    aquamarine3, aquamarine4, azure, azure1, azure2, azure3, azure4, beige, bisque, bisque1, bisque2, bisque3, bisque4, black,
    blanchedalmond, blue, blue1, blue2, blue3, blue4, blueviolet, brown, brown1, brown2, brown3, brown4, burlywood, burlywood1,
    burlywood2, burlywood3, burlywood4, cadetblue, cadetblue1, cadetblue2, cadetblue3, cadetblue4, chartreuse, chartreuse1,
    chartreuse2, chartreuse3, chartreuse4, chocolate, chocolate1, chocolate2, chocolate3, chocolate4, coral, coral1, coral2, coral3,
    coral4, cornflowerblue, cornsilk, cornsilk1, cornsilk2, cornsilk3, cornsilk4, crimson, cyan, cyan1, cyan2, cyan3, cyan4,
    darkgoldenrod, darkgoldenrod1, darkgoldenrod2, darkgoldenrod3, darkgoldenrod4, darkgreen, darkkhaki, darkolivegreen,
    darkolivegreen1, darkolivegreen2, darkolivegreen3, darkolivegreen4, darkorange, darkorange1, darkorange2, darkorange3,
    darkorange4, darkorchid, darkorchid1, darkorchid2, darkorchid3, darkorchid4, darksalmon, darkseagreen, darkseagreen1,
    darkseagreen2, darkseagreen3, darkseagreen4, darkslateblue, darkslategray, darkslategray1, darkslategray2, darkslategray3,
    darkslategray4, darkslategrey, darkturquoise, darkviolet, deeppink, deeppink1, deeppink2, deeppink3, deeppink4, deepskyblue,
    deepskyblue1, deepskyblue2, deepskyblue3, deepskyblue4, dimgray, dimgrey, dodgerblue, dodgerblue1, dodgerblue2, dodgerblue3,
    dodgerblue4, firebrick, firebrick1, firebrick2, firebrick3, firebrick4, floralwhite, forestgreen, gainsboro, ghostwhite, gold,
    gold1, gold2, gold3, gold4, goldenrod, goldenrod1, goldenrod2, goldenrod3, goldenrod4, gray, gray0, gray1, gray10, gray100,
    gray11, gray12, gray13, gray14, gray15, gray16, gray17, gray18, gray19, gray2, gray20, gray21, gray22, gray23, gray24, gray25,
    gray26, gray27, gray28, gray29, gray3, gray30, gray31, gray32, gray33, gray34, gray35, gray36, gray37, gray38, gray39, gray4,
    gray40, gray41, gray42, gray43, gray44, gray45, gray46, gray47, gray48, gray49, gray5, gray50, gray51, gray52, gray53, gray54,
    gray55, gray56, gray57, gray58, gray59, gray6, gray60, gray61, gray62, gray63, gray64, gray65, gray66, gray67, gray68, gray69,
    gray7, gray70, gray71, gray72, gray73, gray74, gray75, gray76, gray77, gray78, gray79, gray8, gray80, gray81, gray82, gray83,
    gray84, gray85, gray86, gray87, gray88, gray89, gray9, gray90, gray91, gray92, gray93, gray94, gray95, gray96, gray97, gray98,
    gray99, green, green1, green2, green3, green4, greenyellow, grey, grey0, grey1, grey10, grey100, grey11, grey12, grey13, grey14,
    grey15, grey16, grey17, grey18, grey19, grey2, grey20, grey21, grey22, grey23, grey24, grey25, grey26, grey27, grey28, grey29,
    grey3, grey30, grey31, grey32, grey33, grey34, grey35, grey36, grey37, grey38, grey39, grey4, grey40, grey41, grey42, grey43,
    grey44, grey45, grey46, grey47, grey48, grey49, grey5, grey50, grey51, grey52, grey53, grey54, grey55, grey56, grey57, grey58,
    grey59, grey6, grey60, grey61, grey62, grey63, grey64, grey65, grey66, grey67, grey68, grey69, grey7, grey70, grey71, grey72,
    grey73, grey74, grey75, grey76, grey77, grey78, grey79, grey8, grey80, grey81, grey82, grey83, grey84, grey85, grey86, grey87,
    grey88, grey89, grey9, grey90, grey91, grey92, grey93, grey94, grey95, grey96, grey97, grey98, grey99, honeydew, honeydew1,
    honeydew2, honeydew3, honeydew4, hotpink, hotpink1, hotpink2, hotpink3, hotpink4, indianred, indianred1, indianred2, indianred3,
    indianred4, indigo, invis, ivory, ivory1, ivory2, ivory3, ivory4, khaki, khaki1, khaki2, khaki3, khaki4, lavender,
    lavenderblush, lavenderblush1, lavenderblush2, lavenderblush3, lavenderblush4, lawngreen, lemonchiffon, lemonchiffon1,
    lemonchiffon2, lemonchiffon3, lemonchiffon4, lightblue, lightblue1, lightblue2, lightblue3, lightblue4, lightcoral, lightcyan,
    lightcyan1, lightcyan2, lightcyan3, lightcyan4, lightgoldenrod, lightgoldenrod1, lightgoldenrod2, lightgoldenrod3,
    lightgoldenrod4, lightgoldenrodyellow, lightgray, lightgrey, lightpink, lightpink1, lightpink2, lightpink3, lightpink4,
    lightsalmon, lightsalmon1, lightsalmon2, lightsalmon3, lightsalmon4, lightseagreen, lightskyblue, lightskyblue1, lightskyblue2,
    lightskyblue3, lightskyblue4, lightslateblue, lightslategray, lightslategrey, lightsteelblue, lightsteelblue1, lightsteelblue2,
    lightsteelblue3, lightsteelblue4, lightyellow, lightyellow1, lightyellow2, lightyellow3, lightyellow4, limegreen, linen,
    magenta, magenta1, magenta2, magenta3, magenta4, maroon, maroon1, maroon2, maroon3, maroon4, mediumaquamarine, mediumblue,
    mediumorchid, mediumorchid1, mediumorchid2, mediumorchid3, mediumorchid4, mediumpurple, mediumpurple1, mediumpurple2,
    mediumpurple3, mediumpurple4, mediumseagreen, mediumslateblue, mediumspringgreen, mediumturquoise, mediumvioletred,
    midnightblue, mintcream, mistyrose, mistyrose1, mistyrose2, mistyrose3, mistyrose4, moccasin, navajowhite, navajowhite1,
    navajowhite2, navajowhite3, navajowhite4, navy, navyblue, none, oldlace, olivedrab, olivedrab1, olivedrab2, olivedrab3,
    olivedrab4, orange, orange1, orange2, orange3, orange4, orangered, orangered1, orangered2, orangered3, orangered4, orchid,
    orchid1, orchid2, orchid3, orchid4, palegoldenrod, palegreen, palegreen1, palegreen2, palegreen3, palegreen4, paleturquoise,
    paleturquoise1, paleturquoise2, paleturquoise3, paleturquoise4, palevioletred, palevioletred1, palevioletred2, palevioletred3,
    palevioletred4, papayawhip, peachpuff, peachpuff1, peachpuff2, peachpuff3, peachpuff4, peru, pink, pink1, pink2, pink3, pink4,
    plum, plum1, plum2, plum3, plum4, powderblue, purple, purple1, purple2, purple3, purple4, red, red1, red2, red3, red4,
    rosybrown, rosybrown1, rosybrown2, rosybrown3, rosybrown4, royalblue, royalblue1, royalblue2, royalblue3, royalblue4,
    saddlebrown, salmon, salmon1, salmon2, salmon3, salmon4, sandybrown, seagreen, seagreen1, seagreen2, seagreen3, seagreen4,
    seashell, seashell1, seashell2, seashell3, seashell4, sienna, sienna1, sienna2, sienna3, sienna4, skyblue, skyblue1, skyblue2,
    skyblue3, skyblue4, slateblue, slateblue1, slateblue2, slateblue3, slateblue4, slategray, slategray1, slategray2, slategray3,
    slategray4, slategrey, snow, snow1, snow2, snow3, snow4, springgreen, springgreen1, springgreen2, springgreen3, springgreen4,
    steelblue, steelblue1, steelblue2, steelblue3, steelblue4, tan, tan1, tan2, tan3, tan4, thistle, thistle1, thistle2, thistle3,
    thistle4, tomato, tomato1, tomato2, tomato3, tomato4, transparent, turquoise, turquoise1, turquoise2, turquoise3, turquoise4,
    violet, violetred, violetred1, violetred2, violetred3, violetred4, wheat, wheat1, wheat2, wheat3, wheat4, white, whitesmoke,
    yellow, yellow1, yellow2, yellow3, yellow4, yellowgreen;

    @Override
    public String colorName() {
      return name().toLowerCase();
    }
  }

  public enum EdgeStyle {
    solid, dashed, dotted, bold,
  }

  public enum NodeStyle {
    solid, dashed, dotted, bold, rounded, diagonals, filled, striped, wedged,
  }

  final static Function<Object, String> XFORM_QUOTED = (in) -> '"' + in.toString() + '"';
  final static Function<Object, String> XFORM_HTML = (in) -> '<' + in.toString() + '>';
  final static Function<Object, String> XFORM_ENUM_LOWERCASE = (in) -> ((Enum<?>) in).name().toLowerCase();
  final static Function<Object, String> XFORM_TOSTRING = Objects::toString;

  public enum RankDir {TB, RL, BT, LR}

  static class Attribute {
    final String name;
    final Object value;
    final Function<Object, String> xform;

    public Attribute(String name, Object value, Function<Object, String> xform) {
      this.name = name;
      this.value = value;
      this.xform = xform;
    }

    public Attribute(String name, Object value) {
      this.name = name;
      this.value = value;
      this.xform = XFORM_TOSTRING;
    }

    String transform() {
      if (value != null) {
        return xform.apply(value);
      }
      return null;
    }

    void emit(List<String> opts) {
      if (value != null) {
        opts.add(name + "=" + xform.apply(value));
      }
    }

    @Override
    public String toString() {
      return "Attribute{" + "name='" + name + '\'' + ", value=" + value + '}';
    }
  }

  interface Attributable<E> {
    E add(Attribute attr);
  }

  public static class BaseAttributable<E> implements Attributable<E> {
    protected final LinkedHashMap<String, Attribute> attributes = new LinkedHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public E add(Attribute attr) {
      attributes.put(attr.name, attr);
      return (E) this;
    }

    protected void emit(List<String> opts) {
      for (Attribute a : attributes.values()) {
        a.emit(opts);
      }
    }

    @Override
    public String toString() {
      return "Attributable{" + "attributes=" + attributes + '}';
    }

    public Map<String, Attribute> getAttributes() {
      return attributes;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }
      BaseAttributable<?> that = (BaseAttributable<?>) o;
      return attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
      return Objects.hash(attributes);
    }
  }

  interface Labeled<E> extends Attributable<E> {
    default E html(String html) {
      return add(new Attribute("label", html, XFORM_HTML));
    }

    default E label(String label) {
      return add(new Attribute("label", label, XFORM_QUOTED));
    }

    default E labelFontColor(String color) {
      return add(new Attribute("fontcolor", color));
    }

    default E labelFontsize(int fontSize) {
      return add(new Attribute("fontsize", fontSize));
    }
  }

  @SuppressWarnings("unchecked")
  interface NodeStyled<E> extends Attributable<E> {
    default E style(NodeStyle style, Color... colorlist) {
      style(style);
      if (colorlist.length > 0) {
        String clist = Arrays.stream(colorlist).map(Object::toString).collect(Collectors.joining(":"));
        return add(new Attribute("fillcolor", clist, XFORM_QUOTED));
      }
      return (E) this;
    }

    default E style(NodeStyle style) {
      return add(new Attribute("style", style.name()));
    }

    default E style(NodeStyle style, String colorlist) {
      style(style);
      return add(new Attribute("fillcolor", colorlist, XFORM_QUOTED));
    }
  }

  @SuppressWarnings("unchecked")
  interface EdgeStyled<E> extends Attributable<E> {
    default E style(EdgeStyle style, Color... colorlist) {
      style(style);
      if (colorlist.length > 0) {
        String clist = Arrays.stream(colorlist).map(Object::toString).collect(Collectors.joining(":"));
        return add(new Attribute("fillcolor", clist, XFORM_QUOTED));
      }
      return (E) this;
    }

    default E style(EdgeStyle style) {
      return add(new Attribute("style", style.name()));
    }

    default E style(EdgeStyle style, String colorlist) {
      style(style);
      return add(new Attribute("fillcolor", colorlist, XFORM_QUOTED));
    }
  }

  interface Colored<E> extends Attributable<E> {
    default E color(String color) {
      return add(new Attribute("color", color));
    }

    default E color(Color color) {
      return add(new Attribute("color", color.colorName()));
    }
  }

  interface Shaped<E> extends Attributable<E> {
    default E shaped(Shapes shape) {
      return add(new Attribute("shape", shape, XFORM_ENUM_LOWERCASE));
    }
  }

  @SuppressWarnings("unchecked")
  interface Commentable<E> {
    List<String> getCommentsBefore();

    List<String> getCommentsAfter();

    default E commentBefore(String... lines) {
      getCommentsBefore().addAll(Arrays.asList(lines));
      return (E) this;
    }

    default E commentAfter(String... lines) {
      getCommentsAfter().addAll(Arrays.asList(lines));
      return (E) this;
    }
  }

  public static class Graph implements Child, Commentable<Graph> {
    private final String name;
    private boolean clustered = false;
    private boolean directed = true;
    private final ArrayList<Child> children = new ArrayList<>();
    private final ArrayList<Connection> connections = new ArrayList<>();
    private RankDir rankDir = null;
    private final List<String> commentsBefore = new LinkedList<>();
    private final List<String> commentsAfter = new LinkedList<>();
    private final List<String> commentsHeader = new LinkedList<>();
    private final List<String> commentsFooter = new LinkedList<>();

    public Graph(String name) {
      this.name = name;
    }

    public List<Child> getChildren() {
      return children;
    }

    public List<Connection> getConnections() {
      return connections;
    }

    public Graph clustered(boolean clustered) {
      this.clustered = clustered;
      return this;
    }

    @Override
    public List<String> getCommentsBefore() {
      return commentsBefore;
    }

    @Override
    public List<String> getCommentsAfter() {
      return commentsAfter;
    }

    public Graph commentHeader(String... lines) {
      this.commentsHeader.addAll(Arrays.asList(lines));
      return this;
    }

    public Graph commentFooter(String... lines) {
      this.commentsFooter.addAll(Arrays.asList(lines));
      return this;
    }

    public Graph directed(boolean directed) {
      this.directed = directed;
      return this;
    }

    public Graph rankdir(RankDir dir) {
      this.rankDir = dir;
      return this;
    }

    public String footer() {
      return "}";
    }

    public String header() {
      String w = (directed ? "digraph " : "graph ");
      return w + name + " {";
    }

    @Override
    public String id() {
      return name;
    }

    @Override
    public String toString() {
      return "Graph{" + "name='" + name + '\'' + ", clustered=" + clustered + ", directed=" + directed + '}';
    }

    @Override
    public void emit(DrawDot dot, PrintWriter writer, String indent) throws IOException {
      for (String s : commentsBefore) {
        writer.println(indent + "// " + s);
      }
      writer.println(indent + (clustered ? "cluster_" : "") + header());
      for (String s : commentsHeader) {
        writer.println(indent + INDENTION + "// " + s);
      }
      if (rankDir != null) {
        writer.println(indent + INDENTION + "rankdir=\"" + rankDir.name() + '"');
      }
      for (Child child : children) {
        child.emit(dot, writer, indent + INDENTION);
      }
      for (Connection connection : connections) {
        connection.emit(this, writer, indent + INDENTION);
      }
      writer.println(indent + footer());
      for (String s : commentsFooter) {
        writer.println(indent + INDENTION + "// " + s);
      }
      for (String s : commentsAfter) {
        writer.println(indent + "// " + s);
      }
    }

    public Graph add(Child child) {
      children.add(child);
      return this;
    }

    public Graph add(Connection connection) {
      connections.add(connection);
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }
      Graph graph = (Graph) o;
      return name.equals(graph.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  public interface Child {
    void emit(DrawDot dot, PrintWriter writer, String indent) throws IOException;

    String id();
  }

  public static class Node extends BaseAttributable<Node>
    implements Child, Colored<Node>, Labeled<Node>, Shaped<Node>, NodeStyled<Node>, Commentable<Node> {
    private final String id;
    private final List<String> commentsBefore = new LinkedList<>();
    private final List<String> commentsAfter = new LinkedList<>();

    public Node(String id) {
      this.id = id;
    }

    @Override
    public List<String> getCommentsBefore() {
      return commentsBefore;
    }

    @Override
    public List<String> getCommentsAfter() {
      return commentsAfter;
    }

    Node recordV(String... labels) {
      shaped(Shapes.RECORD);
      return label("{" + String.join(" | ", labels) + "}");
    }

    Node recordH(String... labels) {
      shaped(Shapes.RECORD);
      return label(String.join(" | ", labels));
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public void emit(DrawDot dot, PrintWriter writer, String indent) {
      for (String s : commentsBefore) {
        writer.println(indent + "// " + s);
      }
      writer.print(indent);
      writer.print('"' + id() + "\" ");
      List<String> opts = new LinkedList<>();
      emit(opts);
      if (!opts.isEmpty()) {
        writer.print(opts);
      }
      writer.println();
      for (String s : commentsAfter) {
        writer.println(indent + "// " + s);
      }
    }

    @Override
    public String toString() {
      return "Node{" + "id='" + id + '\'' + "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }
      Node node = (Node) o;
      return id.equals(node.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  public enum Arrows {
    BOX, CROW, CURVE, DIAMOND, DOT, ICURVE, INV, NONE, NORMAL, TEE, VEE;

    Arrow arrow() {
      return new Arrow(this);
    }
  }

  public static class Arrow extends BaseAttributable<Arrow> implements Colored<Arrow>, Labeled<Arrow> {
    private final Arrows type;

    private Arrow(Arrows type) {
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) { return true; }
      if (o == null || getClass() != o.getClass()) { return false; }
      Arrow arrow = (Arrow) o;
      return type == arrow.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(type);
    }
  }

  public static class Shape extends BaseAttributable<Shape> {
    private final Shapes shape;

    public Shape() {
      this.shape = null;
    }

    public Shape(Shapes shape) {
      this.shape = shape;
    }
  }

  public enum Shapes {
    BOX, POLYGON, ELLIPSE, OVAL, CIRCLE, POINT, EGG, TRIANGLE, PLAINTEXT, PLAIN, DIAMOND, TRAPEZIUM, PARALLELOGRAM, HOUSE, PENTAGON,
    HEXAGON, SEPTAGON, OCTAGON, DOUBLECIRCLE, DOUBLEOCTAGON, TRIPLEOCTAGON, INVTRIANGLE, INVTRAPEZIUM, INVHOUSE, MDIAMOND, MSQUARE,
    MCIRCLE, RECT, RECTANGLE, SQUARE, STAR, NONE, UNDERLINE, CYLINDER, NOTE, TAB, FOLDER, BOX3D, COMPONENT, PROMOTER, CDS,
    TERMINATOR, UTR, PRIMERSITE, RESTRICTIONSITE, FIVEPOVERHANG, THREEPOVERHANG, NOVERHANG, ASSEMBLY, SIGNATURE, INSULATOR,
    RIBOSITE, RNASTAB, PROTEASESITE, PROTEINSTAB, RPROMOTER, RARROW, LARROW, LPROMOTER, MRECORD, RECORD

  }

  public enum ConnectionType {
    FORWARD, BACK, BOTH, NONE
  }

  public static class Connection extends BaseAttributable<Connection>
    implements Labeled<Connection>, Colored<Connection>, EdgeStyled<Connection>, Commentable<Connection> {
    private final Node from;
    private final String fromPort;
    private final Node to;
    private final String toPort;
    private Arrow head;
    private Arrow tail;
    private final List<String> commentsBefore = new LinkedList<>();
    private final List<String> commentsAfter = new LinkedList<>();

    public Connection(Node from, Node to) {
      this(from, null, to, null);
    }

    public Connection(Node from, String fromPort, Node to, String toPort) {
      this.from = Objects.requireNonNull(from);
      this.fromPort = fromPort;
      this.to = Objects.requireNonNull(to);
      this.toPort = toPort;
    }

    @Override
    public List<String> getCommentsBefore() {
      return commentsBefore;
    }

    @Override
    public List<String> getCommentsAfter() {
      return commentsAfter;
    }

    public Connection penWidth(int penWidth) {
      add(new Attribute("penwidth", penWidth));
      return this;
    }

    public Node getFrom() {
      return from;
    }

    public String getFromPort() {
      return fromPort;
    }

    public Node getTo() {
      return to;
    }

    public String getToPort() {
      return toPort;
    }

    public Arrow getHead() {
      return head;
    }

    public Arrow getTail() {
      return tail;
    }

    public Connection connectionType(ConnectionType cType) {
      add(new Attribute("dir", cType));
      return this;
    }

    public void emit(Graph parent, PrintWriter writer, String indent) {
      for (String s : commentsBefore) {
        writer.println(indent + "// " + s);
      }
      writer.print(indent);
      writer.print('"' + from.id() + '"');
      if (fromPort != null) {
        writer.print(":\"" + fromPort + '"');
      }
      writer.print(parent.directed ? " -> " : " -- ");
      writer.print('"' + to.id() + '"');
      if (toPort != null) {
        writer.print(":\"" + toPort + '"');
      }
      LinkedList<String> opts = new LinkedList<>();
      emit(opts);
      if (head != null) {
        head.emit(opts);
      }
      if (tail != null) {
        tail.emit(opts);
      }
      if (!opts.isEmpty()) {
        writer.print(" " + opts);
      }
      writer.println();
      for (String s : commentsAfter) {
        writer.println(indent + "// " + s);
      }
    }

    public Connection head(Arrow head) {
      this.head = head;
      return this;
    }

    public Connection tail(Arrow tail) {
      this.tail = tail;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      return (this == o);
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return "Connection{" + "from=" + from + ", to=" + to + ", head=" + head + ", tail=" + tail + "} " + super.toString();
    }
  }

  private final Graph root;

  public DrawDot(final String name) {
    this.root = new Graph(name);
  }

  public Graph root() {
    return root;
  }

  public void emit(Writer writer) throws IOException {
    PrintWriter pw = new PrintWriter(writer);
    root.emit(this, pw, "");
    pw.flush();
  }

  public void emit(PrintWriter writer) throws IOException {
    root.emit(this, writer, "");
  }

  public static String escapeForDotHTML(String str) {
    boolean initted = false;

    StringBuilder sb = null;
    for (int i = 0; i < str.length(); i++) {
      char ch = str.charAt(i);
      switch (ch) {
        case '&':
          if (!initted) {
            sb = new StringBuilder();
            sb.append(str, 0, i);
            initted = true;
          }
          sb.append("&amp;");
          break;
        case '"':
          if (!initted) {
            sb = new StringBuilder();
            sb.append(str, 0, i);
            initted = true;
          }
          sb.append("&quot;");
          break;
        case '<':
          if (!initted) {
            sb = new StringBuilder();
            sb.append(str, 0, i);
            initted = true;
          }
          sb.append("&lt;");
          break;
        case '>':
          if (!initted) {
            sb = new StringBuilder();
            sb.append(str, 0, i);
            initted = true;
          }
          sb.append("&gt;");
          break;
        default:
          if (initted) {
            sb.append(ch);
          }
          break;
      }
    }
    if (initted) {
      return sb.toString();
    }
    return str;
  }
}

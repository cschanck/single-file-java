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

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.sfj.DrawDot.Arrows;
import static org.sfj.DrawDot.Connection;
import static org.sfj.DrawDot.Node;
import static org.sfj.StringsCompare.LineSource;
import static org.sfj.StringsCompare.SKIP_BLANK;
import static org.sfj.StringsCompare.TRIM_LEADING_AND_TRAILING_WHITESPACE;
import static org.sfj.StringsCompare.source;
import static org.sfj.StringsCompare.stringsCompare;

public class DrawDotTest {

  @Test
  public void testNodes() {
    Node node1 = new Node("a");
    Node node2 = new Node("b");
    Node node3 = new Node("a");

    assertThat(node1, is(node1));
    assertThat(node1, is(node3));
    assertThat(node1, not(is(node2)));

    node1.color(DrawDot.ColorsSVG.aliceblue)
         .shaped(DrawDot.Shapes.ELLIPSE)
         .commentBefore("before")
         .commentAfter("after")
         .style(DrawDot.NodeStyle.bold)
         .label("label");

    assertThat(node1.getAttributes().get("color").value.toString(), is(DrawDot.ColorsSVG.aliceblue.name()));
    assertThat(node1.getAttributes().get("shape").value.toString(), is(DrawDot.Shapes.ELLIPSE.name()));
    assertThat(node1.getAttributes().get("label").value.toString(), is("label"));
    assertThat(node1.getAttributes().get("style").value.toString(), is(DrawDot.NodeStyle.bold.name()));
    assertThat(node1.getCommentsBefore().get(0), is("before"));
    assertThat(node1.getCommentsAfter().get(0), is("after"));

    node2.style(DrawDot.NodeStyle.diagonals, DrawDot.ColorsSVG.cadetblue, DrawDot.ColorsSVG.antiquewhite).html("<b>test</b>");
    assertThat(node2.getAttributes().get("style").value.toString(), is(DrawDot.NodeStyle.diagonals.name()));
    assertThat(node2.getAttributes().get("fillcolor").value.toString(),
      is(DrawDot.ColorsSVG.cadetblue + ":" + DrawDot.ColorsSVG.antiquewhite));
    assertThat(node2.getAttributes().get("label").value.toString(), is("<b>test</b>"));
  }

  @Test
  public void testConnections() {
    Node node1 = new Node("a");
    Node node2 = new Node("b");
    Connection c1 = new Connection(node1, node2);
    Connection c2 = new Connection(node2, node1);
    Connection c3 = new Connection(node1, node2);
    assertThat(c1, not(is(c2)));
    assertThat(c1, not(is(c3)));
    assertThat(c1, is(c1));

    c1.head(Arrows.DIAMOND.arrow()).penWidth(4).color(DrawDot.ColorsSVG.antiquewhite).label("arrgh");

    assertThat(c1.getHead(), is(Arrows.DIAMOND.arrow()));
    assertThat(c1.getAttributes().get("penwidth").value, is(4));
    assertThat(c1.getAttributes().get("color").value, is(DrawDot.ColorsSVG.antiquewhite.name()));
    assertThat(c1.getAttributes().get("label").value, is("arrgh"));

  }

  @Test
  public void testSimple() throws IOException {
    DrawDot dot = new DrawDot("top");
    dot.root().add(new Node("a"));
    dot.root().add(new Node("b"));
    dot.root().add(new Node("c").label("Happy!"));
    dot.root().add(new Node("d").recordH("one", "two", "three").color("green"));
    dot.root().add(new Node("e").recordV("one", "two", "three").color("green"));
    dot.root().add(new Connection(new Node("a"), new Node("b")).color("red").head(Arrows.CROW.arrow().label("foo arrow")));
    dot.root().add(new Connection(new Node("a"), new Node("c")));
    dot.root().add(new Connection(new Node("d"), new Node("c")));
    dot.root().add(new Connection(new Node("e"), new Node("c")));

    DrawDot.Graph g = dot.root();
    assertThat(g.getChildren().size(), is(5));
    assertThat(g.getChildren().get(0).id(), is("a"));
    assertThat(g.getChildren().get(1).id(), is("b"));
    assertThat(g.getChildren().get(2).id(), is("c"));
    assertThat(g.getChildren().get(3).id(), is("d"));
    assertThat(g.getChildren().get(4).id(), is("e"));

    assertThat(g.getConnections().size(), is(4));
    assertThat(g.getConnections().get(0).getFrom().id(), is("a"));
    assertThat(g.getConnections().get(1).getFrom().id(), is("a"));
    assertThat(g.getConnections().get(2).getFrom().id(), is("d"));
    assertThat(g.getConnections().get(3).getFrom().id(), is("e"));
    assertThat(g.getConnections().get(0).getTo().id(), is("b"));
    assertThat(g.getConnections().get(1).getTo().id(), is("c"));
    assertThat(g.getConnections().get(2).getTo().id(), is("c"));
    assertThat(g.getConnections().get(3).getTo().id(), is("c"));
    StringWriter sw = new StringWriter();
    dot.emit(sw);
    LineSource left = source(sw.toString());
    LineSource right = source(DrawDotTest.this.getClass(), "drawdot1.dot");
    boolean cmp = stringsCompare(left, right, SKIP_BLANK, TRIM_LEADING_AND_TRAILING_WHITESPACE);
    assertThat(cmp, is(true));
  }

  @Test
  public void testRecursive() throws IOException {
    DrawDot dot = new DrawDot("top");
    dot.root().add(new Node("a"));
    dot.root().add(new Node("b"));
    dot.root().add(new Node("c"));
    dot.root().add(new Connection(new Node("a"), new Node("b")));
    dot.root().add(new Connection(new Node("b"), new Node("c")));
    dot.root().add(new Connection(new Node("c"), new Node("b")));
    dot.root().add(new Connection(new Node("c"), new Node("a")));
    StringWriter sw = new StringWriter();
    dot.emit(sw);
    LineSource left = source(sw.toString());
    LineSource right = source(DrawDotTest.this.getClass(), "drawdot2.dot");
    boolean cmp = stringsCompare(left, right, SKIP_BLANK, TRIM_LEADING_AND_TRAILING_WHITESPACE);
    assertThat(cmp, is(true));
  }

}

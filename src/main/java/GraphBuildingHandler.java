import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *  Parses OSM XML files using an XML SAX parser. Used to construct the graph of roads for
 *  pathfinding, under some constraints.
 *  See OSM documentation on
 *  <a href="http://wiki.openstreetmap.org/wiki/Key:highway">the highway tag</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Way">the way XML element</a>,
 *  <a href="http://wiki.openstreetmap.org/wiki/Node">the node XML element</a>,
 *  and the java
 *  <a href="https://docs.oracle.com/javase/tutorial/jaxp/sax/parsing.html">SAX parser tutorial</a>.
 *
 */
public class GraphBuildingHandler extends DefaultHandler {
    /**
     * Only allow for non-service roads; this prevents going on pedestrian streets as much as
     * possible. Note that in Berkeley, many of the campus roads are tagged as motor vehicle
     * roads, but in practice we walk all over them with such impunity that we forget cars can
     * actually drive on them.
     */
    private static final Set<String> ALLOWED_HIGHWAY_TYPES = new HashSet<>(Arrays.asList
            ("motorway", "trunk", "primary", "secondary", "tertiary", "unclassified",
                    "residential", "living_street", "motorway_link", "trunk_link", "primary_link",
                    "secondary_link", "tertiary_link"));
    private String activeState = "";
    private final GraphDB g;
    /** valid flag. */
    private boolean validWay = false;
    private GraphDB.Way way;
    private GraphDB.Node node;
    private String wayname;

    /**
     * Create a new GraphBuildingHandler.
     * @param g The graph to populate with the XML data.
     */
    public GraphBuildingHandler(GraphDB g) {
        this.g = g;
        node = null;
        way = null;
    }

    /**
     * Called at the beginning of an element. Typically, you will want to handle each element in
     * here, and you may want to track the parent element.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available. This tells us which element we're looking at.
     * @param attributes The attributes attached to the element. If there are no attributes, it
     *                   shall be an empty Attributes object.
     * @throws SAXException Any SAX exception, possibly wrapping another exception.
     * @see Attributes
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        /* set the valid flag. */
        /* Some example code on how you might begin to parse XML files. */
        if (qName.equals("node")) {
            /* We encountered a new <node...> tag. */
            activeState = "node";
//            System.out.println("Node id: " + attributes.getValue("id"));
//            System.out.println("Node lon: " + attributes.getValue("lon"));
//            System.out.println("Node lat: " + attributes.getValue("lat"));

            /* Hint: A graph-like structure would be nice. */
            /** insert this node to graph g. */
            long id = Long.parseLong(attributes.getValue("id"));
            double lon = Double.parseDouble(attributes.getValue("lon"));
            double lat = Double.parseDouble(attributes.getValue("lat"));
            node = new GraphDB.Node(id, lon, lat);
//            System.out.println("lon and lat of a node" + lon + ", " + lat);
        } else if (qName.equals("way")) {
            /* We encountered a new <way...> tag. */
            activeState = "way";
//            System.out.println("Beginning a way...");

            /** store a way determine if it is valid. */
            long id = Long.parseLong(attributes.getValue("id"));
            way = new GraphDB.Way(id);
        } else if (activeState.equals("way") && qName.equals("nd")) {
            /* While looking at a way, we found a <nd...> tag. */
//            System.out.println("Id of a node in this way: " + attributes.getValue("ref"));

            /* Hint1: It would be useful to remember what was the last node in this way. */
            /* Hint2: Not all ways are valid. So, directly connecting the nodes here would be
            cumbersome since you might have to remove the connections if you later see a tag that
            makes this way invalid. Instead, think of keeping a list of possible connections and
            remember whether this way is valid or not. */
            long id = Long.parseLong(attributes.getValue("ref"));
            way.wayNodes.add(id);

        } else if (activeState.equals("way") && qName.equals("tag")) {
            /* While looking at a way, we found a <tag...> tag. */
            String k = attributes.getValue("k");
            String v = attributes.getValue("v");
            if (k.equals("maxspeed")) {
//                System.out.println("Max Speed: " + v);
            } else if (k.equals("highway")) {
//                System.out.println("Highway type: " + v);
                /* Hint: Setting a "flag" is good enough! */
                if (ALLOWED_HIGHWAY_TYPES.contains(v)) {
                    validWay = true;
                }
            } else if (k.equals("name:en")) {
                wayname = v;
//                System.out.println("Way Name: " + v);
            }
            way.infos.put(k, v);
//            System.out.println("Tag with k=" + k + ", v=" + v + ".");
        } else if (activeState.equals("node") && qName.equals("tag") && attributes.getValue("k")
                .equals("name:en")) {
            /* While looking at a node, we found a <tag...> with k="name". */
            /* Hint: Since we found this <tag...> INSIDE a node, we should probably remember which
            node this tag belongs to. Remember XML is parsed top-to-bottom, so probably it's the
            last node that you looked at (check the first if-case). */

            String name = attributes.getValue("v");
            node.infos.put("name:en", name);
            g.search.add(node.infos.get("name:en"), node.id);
//            System.out.println("Node's name: " + attributes.getValue("v"));
        }
    }

    /**
     * Receive notification of the end of an element. You may want to take specific terminating
     * actions here, like finalizing vertices or edges found.
     * @param uri The Namespace URI, or the empty string if the element has no Namespace URI or
     *            if Namespace processing is not being performed.
     * @param localName The local name (without prefix), or the empty string if Namespace
     *                  processing is not being performed.
     * @param qName The qualified name (with prefix), or the empty string if qualified names are
     *              not available.
     * @throws SAXException  Any SAX exception, possibly wrapping another exception.
     */
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("way")) {
            /* We are done looking at a way. (We finished looking at the nodes, speeds, etc...)*/
            /* Hint1: If you have stored the possible connections for this way, here's your
            chance to actually connect the nodes together if the way is valid. */
            /** if this is a valid way. */
            if (validWay) {
                /** insert this valid way. */
                g.insertWay(way.id, way);
//                System.out.println("Inserting way: " + way.id);
                /** connect this way's nodes. */
                List<Long> N = way.wayNodes;
                for (int i = 0; i < N.size() - 1; i += 1) {
                    long id1 = N.get(i);
                    long id2 = N.get(i + 1);
                    g.connectNodes(id1, id2);
//                    System.out.println("Connecting two nodes: " + id1 + " and " + id2);
                }
                /** mark the way node with this way name. */
                for (long id : way.wayNodes) {
                    g.nodes.get(id).infos.put("wayName", wayname);
                }
                validWay = false;
            }
//            System.out.println("Finishing a way...");
        }
        if (qName.equals("node")) {
            g.insertNode(node.id, node);
        }
    }

}

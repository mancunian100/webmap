import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Graph for storing all of the intersection (vertex) and road (edge) information.
 * Uses the GraphBuildingHandler to convert the XML files into a graph. A GraphDB
 * object includes the vertices, adjacent, distance, closest, lat, and lon
 * methods.
 *
 */
public class GraphDB {
    /** Your instance variables for storing the graph. You should consider
     * creating helper classes, e.g. Node, Edge, etc. */

    Map<Long, Node> nodes;
    Map<Long, Way> ways;
    Map<Long, Node> removedNodes;
    Trie search = new Trie();

    /**
     * Example constructor shows how to create and start an XML parser.
     * You do not need to modify this constructor, but you're welcome to do so.
     * @param dbPath Path to the XML file to be parsed.
     */
    public GraphDB(String dbPath) {
        nodes = new LinkedHashMap<>();
        ways = new LinkedHashMap<>();
        try {
//            File inputFile = new File(dbPath);
//            FileInputStream inputStream = new FileInputStream(inputFile);
//            // GZIPInputStream stream = new GZIPInputStream(inputStream);
//
//            SAXParserFactory factory = SAXParserFactory.newInstance();
//            SAXParser saxParser = factory.newSAXParser();
//            GraphBuildingHandler gbh = new GraphBuildingHandler(this);
//            saxParser.parse(inputStream, gbh);


            InputStream in = getClass().getClassLoader().getResourceAsStream(dbPath);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            GraphBuildingHandler gbh = new GraphBuildingHandler(this);
            saxParser.parse(in, gbh);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     * @param s Input string.
     * @return Cleaned string.
     */
    static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    /**
     *  Remove nodes with no connections from the graph.
     *  While this does not guarantee that any two nodes in the remaining graph are connected,
     *  we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        if (nodes.size() != 0) {
            removedNodes = new HashMap<>();
            for (long id : nodes.keySet()) {
                Node node = nodes.get(id);
                if (node.adjacency.size() == 0) {
                    removedNodes.put(id, node);
                }
            }
            for (long id : removedNodes.keySet()) {
                nodes.remove(id);
            }
        }
    }

    /**
     * Returns an iterable of all vertex IDs in the graph.
     * @return An iterable of id's of all vertices in the graph.
     */
    Iterable<Long> vertices() {
        //YOUR CODE HERE, this currently returns only an empty list.
        return nodes.keySet();
    }

    /**
     * Returns ids of all vertices adjacent to v.
     * @param v The id of the vertex we are looking adjacent to.
     * @return An iterable of the ids of the neighbors of v.
     */
    Iterable<Long> adjacent(long v) {
        return nodes.get(v).adjacency;
    }

    /**
     * Returns the great-circle distance between vertices v and w in miles.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The great-circle distance between the two locations from the graph.
     */
    double distance(long v, long w) {
        return distance(lon(v), lat(v), lon(w), lat(w));
    }

    static double distance(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double dphi = Math.toRadians(latW - latV);
        double dlambda = Math.toRadians(lonW - lonV);

        double a = Math.sin(dphi / 2.0) * Math.sin(dphi / 2.0);
        a += Math.cos(phi1) * Math.cos(phi2) * Math.sin(dlambda / 2.0) * Math.sin(dlambda / 2.0);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 3963 * c;
    }

    /**
     * Returns the initial bearing (angle) between vertices v and w in degrees.
     * The initial bearing is the angle that, if followed in a straight line
     * along a great-circle arc from the starting point, would take you to the
     * end point.
     * Assumes the lon/lat methods are implemented properly.
     * <a href="https://www.movable-type.co.uk/scripts/latlong.html">Source</a>.
     * @param v The id of the first vertex.
     * @param w The id of the second vertex.
     * @return The initial bearing between the vertices.
     */
    double bearing(long v, long w) {
        return bearing(lon(v), lat(v), lon(w), lat(w));
    }

    static double bearing(double lonV, double latV, double lonW, double latW) {
        double phi1 = Math.toRadians(latV);
        double phi2 = Math.toRadians(latW);
        double lambda1 = Math.toRadians(lonV);
        double lambda2 = Math.toRadians(lonW);

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2);
        x -= Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /**
     * Returns the vertex closest to the given longitude and latitude.
     * @param lon The target longitude.
     * @param lat The target latitude.
     * @return The id of the node in the graph closest to the target.
     */
    long closest(double lon, double lat) {
        double d = Double.MAX_VALUE;
        long closestId = -1;
        for (long id : nodes.keySet()) {
            double idlon = lon(id);
            double idlat = lat(id);
            if (distance(lon, lat, idlon, idlat) < d) {
                closestId = id;
                d = distance(lon, lat, idlon, idlat);
            }
        }
        return closestId;
    }

    /**
     * Gets the longitude of a vertex.
     * @param v The id of the vertex.
     * @return The longitude of the vertex.
     */
    double lon(long v) {
        return nodes.get(v).lon;
    }

    /**
     * Gets the latitude of a vertex.
     * @param v The id of the vertex.
     * @return The latitude of the vertex.
     */
    double lat(long v) {
        return nodes.get(v).lat;
    }

    /**
     * insert a node to this graph.
     * @param id the node id.
     * @param node the node to be inserted.
     */
    void insertNode(long id, Node node) {
        this.nodes.put(id, node);
    }

    /**
     * insert a way to this graph.
     * @param id the way id.
     * @param way the way to be inserted.
     */
    void insertWay(long id, Way way) {
        this.ways.put(id, way);
    }

    /**
     * connect two nodes
     * @param id1 connect node1
     * @param id2 connect node2
     */
    void connectNodes(long id1, long id2) {
        if (nodes.containsKey(id1) && nodes.containsKey(id2)) {
            nodes.get(id1).adjacency.add(id2);
            nodes.get(id2).adjacency.add(id1);
        }
    }

    /**
     * nested Node class.
     */
    static class Node  {
        long id;
        double lon;
        double lat;
        Set<Long> adjacency;
        Map<String, String> infos;

        Node(long id, double lon, double lat) {
            this.id = id;
            this.lon = lon;
            this.lat = lat;
            adjacency = new HashSet<>();
            infos = new HashMap<>();
        }
    }

    /**
     * nested Way class.
     */
    static class Way {
        long id;
        List<Long> wayNodes;
        Map<String, String> infos;

        Way(long id) {
            this.id  = id;
            wayNodes = new ArrayList<>();
            infos = new HashMap<>();
        }
    }

    /** nested Ties class. */
    public class Trie {
        TrieNode root;
        public Trie() {
            root = new TrieNode();
        }
        public class TrieNode {
            boolean exists;
            Map<Character, TrieNode> links;
            List<String> fullName; //prevent collision
            Set<Long> locationID;
            public TrieNode() {
                links = new HashMap<>();
                exists = false;
                fullName = null;
                locationID = null;
            }
        }

        public void add(String name, long id) {
            String cleaned = cleanString(name);
            char[] working = cleaned.toCharArray();
            TrieNode current = root;

            if (cleaned.equals("")) { //edge case for numbered names
                current.exists = true;
                if (current.fullName == null) {
                    current.fullName = new ArrayList<>();
                }
                current.fullName.add(name);
                if (current.locationID == null) {
                    current.locationID = new HashSet<>();
                }
                current.locationID.add(id);
            }

            int endCount = 0;
            for (char i: working) {
                endCount += 1;
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                } else {
                    current.links.put(i, new TrieNode());
                    current = current.links.get(i);
                }
                if (endCount == working.length) { // last node
                    current.exists = true;
                    if (current.fullName == null) { //save memory
                        current.fullName = new ArrayList<>();
                    }
                    current.fullName.add(name);
                    if (current.locationID == null) {
                        current.locationID = new HashSet<>();
                    }
                    current.locationID.add(id);
                }
            }
        }

        //retrieve all strings children of this node
        public void retrieve(TrieNode a, Set<String> result) {
            if (a.exists) {
                for (String i: a.fullName) {
                    result.add(i);
                }

            }
            for (char i: a.links.keySet()) {
                retrieve(a.links.get(i), result);
            }
        }
        public List<String> find(String prefix) {
            String cleaned = cleanString(prefix);
            char[] working = cleaned.toCharArray();
            TrieNode current = root;
            for (char i: working) {
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                } else if (current.links.containsKey(Character.toUpperCase(i))) {
                    current = current.links.get(Character.toUpperCase(i));
                } else {
                    return null;
                }
            }
            //current is now node at last letter, prefix is available
            Set<String> result = new HashSet<>();
            retrieve(current, result);
            return new LinkedList<>(result);
        }

        public TrieNode findTrieNode(String name) {
            char[] working = name.toCharArray();
            TrieNode current = root;
            for (char i: working) {
                if (current.links.containsKey(i)) {
                    current = current.links.get(i);
                }  else {
                    return null;
                }
            }
            return current;
        }
    }
}

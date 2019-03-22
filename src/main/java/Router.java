import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Stack;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * This class provides a shortestPath method for finding routes between two points
 * on the map using A* algorithm.
 */
public class Router {


    /**
     * Return a List of longs representing the shortest path from the node
     * closest to a start location and the node closest to the destination
     * location.
     * @param g The graph to use.
     * @param stlon The longitude of the start location.
     * @param stlat The latitude of the start location.
     * @param destlon The longitude of the destination location.
     * @param destlat The latitude of the destination location.
     * @return A list of node id's in the order visited on the shortest path.
     */
    public static List<Long> shortestPath(GraphDB g, double stlon, double stlat,
                                          double destlon, double destlat) {
        /** A star algorithm to get the shortest path. */
        long startNd = g.closest(stlon, stlat);
        long destNd = g.closest(destlon, destlat);

        /** nested class SearchNode. */
        class SearchNode implements Comparable<SearchNode> {
            long node;
            double disFromStart;
            SearchNode prev;
            double priority;

            SearchNode(long n, double d, SearchNode p) {
                node = n;
                disFromStart = d;
                prev = p;
                priority = d + g.distance(n, destNd);
            }

            @Override
            public int compareTo(SearchNode s) {
                int c = 0;
                if ((this.priority - s.priority) > 0) {
                    c = 1;
                } else if ((this.priority - s.priority) == 0) {
                    c = 0;
                } else if ((this.priority - s.priority) < 0) {
                    c = -1;
                }
                return c;
            }
        }

        /** stack for storing the path node. */
        Stack<Long> path = new Stack<>();
        /** priority for A star. */
        Queue pq = new PriorityQueue();
        /** passed nodes. */
        Set<Long> passed = new HashSet<>();
        passed.add(startNd);
        pq.add(new SearchNode(startNd, 0, null));

        SearchNode presNd = (SearchNode) pq.peek();
        while (presNd.node != destNd) {
            for(long n : g.adjacent(presNd.node)) {
                if (presNd.prev == null || !passed.contains(n)) {
                    double disOfpn = g.distance(presNd.node, n);
                    pq.add(new SearchNode(n, presNd.disFromStart + disOfpn, presNd));
                }
            }
            passed.add(presNd.node);
            presNd = (SearchNode) pq.poll();
        }

        while (presNd != null) {
            path.push(presNd.node);
            presNd = presNd.prev;
        }
        Stack<Long> results = new Stack<>();
        while (path.size() != 0) {
            results.push(path.pop());
        }
        return results;
    }

    /**
     * Create the list of directions corresponding to a route on the graph.
     * @param g The graph to use.
     * @param route The route to translate into directions. Each element
     *              corresponds to a node from the graph in the route.
     * @return A list of NavigatiionDirection objects corresponding to the input
     * route.
     */
    public static List<NavigationDirection> routeDirections(GraphDB g, List<Long> route) {
        List<NavigationDirection> directions = new LinkedList<>();
        boolean start = true;
        long prevNode = route.get(0);
        double distance = 0;

        NavigationDirection temp = new NavigationDirection();
        temp.direction = NavigationDirection.START;
        temp.way = g.nodes.get(route.get(0)).infos.get("wayName");

        for (Long l : route) {
            if (g.nodes.get(l).infos.get("wayName").equals(temp.way)) {
                distance += g.distance(prevNode, l);
            } else {
                distance += g.distance(prevNode, l);
                temp.distance = distance;
                directions.add(temp);

                temp = new NavigationDirection();
                temp.direction = getDirectionInt(g.bearing(prevNode, l));
                if (g.nodes.get(l).infos.get("wayName") == null) {
                    temp.way = NavigationDirection.UNKNOWN_ROAD;
                } else {
                    temp.way = g.nodes.get(l).infos.get("wayName");
                }
                distance = 0;
            }

            prevNode = l;
        }
        return directions;
    }

    /** private helper method for transform bearing angle to int number. */
    private static int getDirectionInt(double bearing) {
        if (bearing > -15 && bearing < 15) {
            return 1;
        } else if (bearing > -30 && bearing < 30) {
            if (bearing < 0) {
                return 2;
            } else {
                return 3;
            }
        } else if (bearing > -100 && bearing < 100) {
            if (bearing < 0) {
                return 4;
            } else {
                return 5;
            }
        } else {
            if (bearing < 0) {
                return 6;
            } else {
                return 7;
            }
        }
    }


    /**
     * Class to represent a navigation direction, which consists of 3 attributes:
     * a direction to go, a way, and the distance to travel for.
     */
    public static class NavigationDirection {

        /** Integer constants representing directions. */
        public static final int START = 0;
        public static final int STRAIGHT = 1;
        public static final int SLIGHT_LEFT = 2;
        public static final int SLIGHT_RIGHT = 3;
        public static final int RIGHT = 4;
        public static final int LEFT = 5;
        public static final int SHARP_LEFT = 6;
        public static final int SHARP_RIGHT = 7;

        /** Number of directions supported. */
        public static final int NUM_DIRECTIONS = 8;

        /** A mapping of integer values to directions.*/
        public static final String[] DIRECTIONS = new String[NUM_DIRECTIONS];

        /** Default name for an unknown way. */
        public static final String UNKNOWN_ROAD = "unknown road";
        
        /** Static initializer. */
        static {
            DIRECTIONS[START] = "Start";
            DIRECTIONS[STRAIGHT] = "Go straight";
            DIRECTIONS[SLIGHT_LEFT] = "Slight left";
            DIRECTIONS[SLIGHT_RIGHT] = "Slight right";
            DIRECTIONS[LEFT] = "Turn left";
            DIRECTIONS[RIGHT] = "Turn right";
            DIRECTIONS[SHARP_LEFT] = "Sharp left";
            DIRECTIONS[SHARP_RIGHT] = "Sharp right";
        }

        /** The direction a given NavigationDirection represents.*/
        int direction;
        /** The name of the way I represent. */
        String way;
        /** The distance along this way I represent. */
        double distance;

        /**
         * Create a default, anonymous NavigationDirection.
         */
        public NavigationDirection() {
            this.direction = STRAIGHT;
            this.way = UNKNOWN_ROAD;
            this.distance = 0.0;
        }

        public String toString() {
            return String.format("%s on %s and continue for %.3f kms.",
                    DIRECTIONS[direction], way, distance * 1.609344);
        }

        /**
         * Takes the string representation of a navigation direction and converts it into
         * a Navigation Direction object.
         * @param dirAsString The string representation of the NavigationDirection.
         * @return A NavigationDirection object representing the input string.
         */
        public static NavigationDirection fromString(String dirAsString) {
            String regex = "([a-zA-Z\\s]+) on ([\\w\\s]*) and continue for ([0-9\\.]+) miles\\.";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(dirAsString);
            NavigationDirection nd = new NavigationDirection();
            if (m.matches()) {
                String direction = m.group(1);
                if (direction.equals("Start")) {
                    nd.direction = NavigationDirection.START;
                } else if (direction.equals("Go straight")) {
                    nd.direction = NavigationDirection.STRAIGHT;
                } else if (direction.equals("Slight left")) {
                    nd.direction = NavigationDirection.SLIGHT_LEFT;
                } else if (direction.equals("Slight right")) {
                    nd.direction = NavigationDirection.SLIGHT_RIGHT;
                } else if (direction.equals("Turn right")) {
                    nd.direction = NavigationDirection.RIGHT;
                } else if (direction.equals("Turn left")) {
                    nd.direction = NavigationDirection.LEFT;
                } else if (direction.equals("Sharp left")) {
                    nd.direction = NavigationDirection.SHARP_LEFT;
                } else if (direction.equals("Sharp right")) {
                    nd.direction = NavigationDirection.SHARP_RIGHT;
                } else {
                    return null;
                }

                nd.way = m.group(2);
                try {
                    nd.distance = Double.parseDouble(m.group(3));
                } catch (NumberFormatException e) {
                    return null;
                }
                return nd;
            } else {
                // not a valid nd
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof NavigationDirection) {
                return direction == ((NavigationDirection) o).direction
                    && way.equals(((NavigationDirection) o).way)
                    && distance == ((NavigationDirection) o).distance;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(direction, way, distance);
        }
    }
}

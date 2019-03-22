import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method returns a Map containing all
 * seven of the required fields.
 */
public class Rasterer {

    private double[] lonDPPs;
    private double[] latDPPs;
    private double rootUllon = MapServer.ROOT_ULLON;
    private double rootLrlon = MapServer.ROOT_LRLON;
    private double rootUllat = MapServer.ROOT_ULLAT;
    private double rootLrlat = MapServer.ROOT_LRLAT;

    public Rasterer() {
        /** LonDPP for all levels of pics. */
        lonDPPs = new double[8];
        latDPPs = new double[8];
        double rootLonDPP = (rootLrlon - rootUllon) / MapServer.TILE_SIZE;
        double rootLatDPP = (rootUllat - rootLrlat) / MapServer.TILE_SIZE;
        for (int i = 0; i <= 7; i += 1) {
            lonDPPs[i] = rootLonDPP / (Math.pow(2, i));
            latDPPs[i] = rootLatDPP / (Math.pow(2, i));
        }
    }

    /**
     * Takes a user query and finds the grid of images that best matches the query. These
     * images will be combined into one big image (rastered) by the front end. <br>
     *
     *     The grid of images must obey the following properties, where image in the
     *     grid is referred to as a "tile".
     *     <ul>
     *         <li>The tiles collected must cover the most longitudinal distance per pixel
     *         (LonDPP) possible, while still covering less than or equal to the amount of
     *         longitudinal distance per pixel in the query box for the user viewport size. </li>
     *         <li>Contains all tiles that intersect the query bounding box that fulfill the
     *         above condition.</li>
     *         <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     *     </ul>
     *
     * @param params Map of the HTTP GET request's query parameters - the query box and
     *               the user viewport width and height.
     *
     * @return A map of results for the front end as specified: <br>
     * "render_grid"   : String[][], the files to display. <br>
     * "raster_ul_lon" : Number, the bounding upper left longitude of the rastered image. <br>
     * "raster_ul_lat" : Number, the bounding upper left latitude of the rastered image. <br>
     * "raster_lr_lon" : Number, the bounding lower right longitude of the rastered image. <br>
     * "raster_lr_lat" : Number, the bounding lower right latitude of the rastered image. <br>
     * "depth"         : Number, the depth of the nodes of the rastered image <br>
     * "query_success" : Boolean, whether the query was able to successfully complete; don't
     *                    forget to set this to true on success! <br>
     */
    public Map<String, Object> getMapRaster(Map<String, Double> params) {
        System.out.println(params);
        Map<String, Object> results = new HashMap<>();

        /** read the params. */
        double queryBoxUllon = params.get("ullon");
        double queryBoxLrlon = params.get("lrlon");
        double queryBoxWidth = params.get("w");

        /** LonDPP of the query box. */
        double queryBoxLonDPP = (queryBoxLrlon - queryBoxUllon) / queryBoxWidth;

        /** depth of pictures. */
        int depth = 0;

        /** select the appropriate level of picture. */
        for (; depth <= 7; depth += 1) {
            if (depth == 7 || lonDPPs[depth] <= queryBoxLonDPP) {
                break;
            }
        }

        /** LonDPP selected. */
        double resultLonDPP = lonDPPs[depth];
        double resultLatDPP = latDPPs[depth];

        /** select the grids of pictures. */
        ArrayList<ArrayList<String>> grids = new ArrayList<>();
        ArrayList<ArrayList<Integer>> rcs = new ArrayList<>();
        for (int r = 0; r < Math.pow(2, depth); r += 1) {
            /** per raw of pictures. */
            ArrayList<String> grid = new ArrayList<>();
            for (int c = 0; c < Math.pow(2, depth); c += 1) {
                double[][] vertices = getVertices(r, c, resultLonDPP, resultLatDPP);
                if (decideCover(vertices, params)) {
                    String g = "d" + depth + "_x" + c + "_y" + r + ".png";
                    grid.add(g);
                    ArrayList<Integer> rc = new ArrayList<>();
                    rc.add(r);
                    rc.add(c);
                    rcs.add(rc);
                }
            }
            if (grid.size() != 0) {
                grids.add(grid);
            }
        }

        /** the lon and lat of the grids. */
        double ullon, ullat, lrlon, lrlat;
        /** upper left vertex raster. */
        int ulr = rcs.get(0).get(0);
        int ulc = rcs.get(0).get(1);
        double[][] ulVertex = getVertices(ulr, ulc, resultLonDPP, resultLatDPP);
        ullon = ulVertex[0][0];
        ullat = ulVertex[0][1];
        /** lower right vertex raster. */
        int len = rcs.size();
        int lrr = rcs.get(len - 1).get(0);
        int lrc = rcs.get(len - 1).get(1);
        double[][] lrVertex = getVertices(lrr, lrc, resultLonDPP, resultLatDPP);
        lrlon = lrVertex[1][0];
        lrlat = lrVertex[1][1];
        /** convert the String ArrayList to array. */
        int rawOfString = grids.size();
        int colOfString = grids.get(0).size();
        String[][] renderGrids = new String[rawOfString][colOfString];

        for (int r = 0; r < rawOfString; r += 1) {
            for (int c = 0; c < colOfString; c += 1) {
                String rc = grids.get(r).get(c);
                renderGrids[r][c] = rc;
            }
        }
        /** return the results. */
        results.put("raster_ul_lon", ullon);
        results.put("raster_ul_lat", ullat);
        results.put("raster_lr_lon", lrlon);
        results.put("raster_lr_lat", lrlat);
        results.put("depth", depth);
        results.put("render_grid", renderGrids);
        results.put("query_success", true);
        System.out.print(results);
        return results;
    }

    /** private helper method for getting the vertices of the given picture block.
     *
     * @param raw the raw if the given picture
     * @param col the column of the given picture
     * @param selectLonDPP the LonDPP if the given picture
     * @return the lon and lat of the four vertices
     */
    private double[][] getVertices(int raw, int col, double selectLonDPP, double selectLatDPP) {
        double[][] vertices = new double[2][2];
        double left = rootUllon + (col * selectLonDPP * MapServer.TILE_SIZE);
        double right = rootUllon + ((col + 1) * selectLonDPP * MapServer.TILE_SIZE);
        double upper = rootUllat - (raw * selectLatDPP * MapServer.TILE_SIZE);
        double lower = rootUllat - ((raw + 1) * selectLatDPP * MapServer.TILE_SIZE);
        double[] vertex00 = {left, upper};
        double[] vertex01 = {right, lower};
        vertices[0] = vertex00;
        vertices[1] = vertex01;
        return vertices;
    }

    /** private helper method for determine if the picture is covered by query box.
     *
     * @param vertices four vertices of the picture.
     * @param params the query box.
     * @return true if query box covers the picture.
     */
    private boolean decideCover(double[][] vertices, Map<String, Double> params) {
        /** query box. */
        double qbleft = params.get("ullon");
        double qbright = params.get("lrlon");
        double qbupper = params.get("ullat");
        double qblower = params.get("lrlat");

        /** vertices of pictures. */
        double[][] V = {
                {vertices[0][0], vertices[0][1]},
                {vertices[1][0], vertices[0][1]},
                {vertices[0][0], vertices[1][1]},
                {vertices[1][0], vertices[1][1]}
        };

        // condition1: the query box covers one of the vertices.
        for (double[] v : V) {
            if ((qbleft <= v[0] && v[0] <= qbright)
                    && (qblower <= v[1] && v[1] <= qbupper)) {
                return true;
            }
        }

        // condition2: the four vertices could cover the query box.
        boolean v1 = qbleft >= V[0][0] && qbupper <= V[0][1];
        boolean v2 = qbright <= V[1][0] && qbupper <= V[1][1];
        boolean v3 = qbleft >= V[2][0] && qblower >= V[2][1];
        boolean v4 = qbright <= V[3][0] && qblower >= V[3][1];
        if (v1 && v2 && v3 && v4) {
            return true;
        }

        return false;
    }

}

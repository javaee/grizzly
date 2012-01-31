/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.memcached.benchmark;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.DatasetUtilities;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Bongjae Chang
 */
public class ChartGenerator {

    private static final int HEIGHT = 600;
    private static final String DEFAULT_RESULT_IMAGES_DIR = "extras/memcached/benchmark/result/images";

    private static final String[] ROW_KEYS_GET = new String[]{"GrizzlyMemcached_GET", "GrizzlyMemcached_GET_Multi", "SpyMemcached_GET", "SpyMemcached_GET_Multi", "JavaMemcached_GET", "JavaMemcached_GET_Multi", "XMemcached_GET", "XMemcached_GET_Multi"};
    private static final String[] ROW_KEYS_SET = new String[]{"GrizzlyMemcached_SET", "GrizzlyMemcached_SET_Multi", "SpyMemcached_SET", "JavaMemcached_SET", "XMemcached_SET"};
    private static final String[] COL_KEYS = new String[]{"1", "50", "100", "200", "400"};

    private enum ResultType {
        GET, SET
    }

    private final ArrayList<DataSet> dataList = new ArrayList<DataSet>();

    private void prepareResult() {
        // get 32bytes
        dataList.add(new DataSet(32,
                ResultType.GET,
                new double[][]{
                        {10351, 250501, 270233, 272099, 278949}, // grizzly get
                        {201005, 1397624, 1588562, 1747869, 1805257}, // grizzly get multi
                        {9436, 145011, 177038, 191296, 170752}, // spy get
                        {217391, 288517, 302823, 253581, 231247}, // spy get multi
                        {14234, 322268, 328434, 330101, 323016}, // java get
                        {207253, 968992, 981113, 983405, 994282}, // java get multi
                        {8125, 144623, 187344, 198624, 203505}, // x get
                        {222222, 287728, 282985, 239743, 218653} // x get multi
                }));

        // set 32bytes
        dataList.add(new DataSet(32,
                ResultType.SET,
                new double[][]{
                        {9928, 251667, 274273, 286409, 288579}, // grizzly set
                        {199004, 827814, 804020, 773619, 719618}, // grizzly set multi
                        {8667, 152137, 193031, 199094, 214230}, // spy set
                        {14015, 331674, 334700, 338782, 337268}, // java set
                        {8075, 165576, 210260, 267192, 257802} // x set
                }));

        // get 64bytes
        dataList.add(new DataSet(64,
                ResultType.GET,
                new double[][]{
                        {10042, 242718, 266595, 280219, 276061},
                        {198019, 1064395, 1118255, 1186239, 1219326},
                        {8898, 140964, 175384, 189138, 168740},
                        {215053, 294204, 295202, 248895, 234250},
                        {14184, 323781, 327788, 323062, 327225},
                        {204081, 981354, 1014970, 1039501, 1040921},
                        {8312, 150489, 185494, 197029, 198336},
                        {224719, 287811, 282246, 235807, 216693}
                }));

        // set 64bytes
        dataList.add(new DataSet(64,
                ResultType.SET,
                new double[][]{
                        {10211, 253903, 277777, 288641, 287909},
                        {181818, 858369, 814332, 781937, 728796},
                        {8547, 144404, 177289, 191777, 194692},
                        {13937, 328245, 337980, 338968, 341275},
                        {8072, 165878, 207846, 261549, 215633}
                }));

        // get 128bytes
        dataList.add(new DataSet(128,
                ResultType.GET,
                new double[][]{
                        {9801, 241867, 267701, 278028, 269120},
                        {194174, 676132, 697107, 719748, 733104},
                        {8598, 141914, 171497, 179103, 172969},
                        {206185, 283728, 277951, 233474, 222816},
                        {13893, 316957, 333139, 327144, 323513},
                        {195121, 644122, 661047, 678253, 692400},
                        {8022, 146821, 182965, 207200, 203982},
                        {220994, 288142, 279994, 235059, 225574}
                }));

        // set 128bytes
        dataList.add(new DataSet(128,
                ResultType.SET,
                new double[][]{
                        {10020, 249003, 274329, 285011, 279090},
                        {182648, 701508, 697593, 693060, 692880},
                        {8483, 138859, 157140, 186328, 182957},
                        {13619, 315955, 334532, 342656, 328764},
                        {7939, 153787, 190439, 242666, 196241}
                }));

        // get 256bytes
        dataList.add(new DataSet(256,
                ResultType.GET,
                new double[][]{
                        {10000, 238891, 262381, 274339, 269832},
                        {170212, 388349, 396118, 402070, 406824},
                        {9436, 126766, 153745, 167021, 157342},
                        {177777, 276740, 266329, 229377, 214796},
                        {13360, 305110, 320769, 327721, 320892},
                        {169491, 375375, 383141, 389863, 393981},
                        {8016, 146950, 186211, 200767, 199717},
                        {182648, 289100, 275046, 234783, 217152}
                }));

        // set 256bytes
        dataList.add(new DataSet(256,
                ResultType.SET,
                new double[][]{
                        {9758, 241254, 263313, 277854, 275581},
                        {160000, 398247, 397022, 396059, 395520},
                        {8328, 106490, 147836, 178229, 188801},
                        {13050, 293427, 307952, 316530, 319431},
                        {7804, 149655, 180277, 207173, 189024}
                }));

        // get 512bytes
        dataList.add(new DataSet(512,
                ResultType.GET,
                new double[][]{
                        {9153, 195102, 196734, 196661, 196425},
                        {125391, 211148, 213015, 214759, 216198},
                        {9128, 105335, 118944, 132994, 123811},
                        {125391, 217296, 211405, 217645, 217675},
                        {12307, 194609, 197170, 195963, 197338},
                        {121951, 207189, 209380, 211288, 212410},
                        {8041, 144206, 179227, 184183, 189163},
                        {127388, 213949, 214247, 214092, 213935}
                }));

        // set 512bytes
        dataList.add(new DataSet(512,
                ResultType.SET,
                new double[][]{
                        {8970, 190349, 191241, 192571, 193958},
                        {116618, 213128, 213151, 212573, 212477},
                        {8476, 100130, 117931, 139896, 135837},
                        {12124, 190005, 192715, 193302, 193915},
                        {7585, 127713, 191874, 196049, 184646}
                }));
    }

    private void generateThreadsTPSChart() {
        final String categoryAxisTitle = "Threads";
        final String valueAxisTitle = "TPS";
        for (DataSet data : dataList) {
            final StringBuilder titleBuilder = new StringBuilder(256);
            titleBuilder.append("MultiKeys = 200, ValueSize = ").append(data.valueSize).append("bytes").append(", LoopCntsPerThread = 200");
            final CategoryDataset dataset = DatasetUtilities.createCategoryDataset(data.rowKeys, COL_KEYS, data.data);
            final JFreeChart freeChart = createChart(dataset, categoryAxisTitle, valueAxisTitle, titleBuilder.toString());

            final StringBuilder fileNameBuilder = new StringBuilder(128);
            fileNameBuilder.append(DEFAULT_RESULT_IMAGES_DIR).append(data.type == ResultType.GET ? "/get_" : "/set_").append(data.valueSize).append("bytes.jpg");
            saveAsFile(freeChart, fileNameBuilder.toString(), (int) (HEIGHT * 1.6), HEIGHT);
        }
    }

    private static JFreeChart createChart(final CategoryDataset categoryDataset, final String categoryAxisLabel, final String valueAxisLabel, final String chartTitle) {
        final JFreeChart jfreechart = ChartFactory.createLineChart(chartTitle, categoryAxisLabel, valueAxisLabel, categoryDataset, PlotOrientation.VERTICAL, true, false, false);
        final LegendTitle legend = jfreechart.getLegend();
        legend.setItemFont(new Font("Dotum", Font.BOLD, 16));
        final CategoryPlot plot = (CategoryPlot) jfreechart.getPlot();
        final LineAndShapeRenderer render = (LineAndShapeRenderer) plot.getRenderer();
        render.setSeriesPaint(0, Color.RED);
        render.setSeriesPaint(1, Color.GREEN);
        render.setSeriesPaint(2, Color.YELLOW);
        render.setSeriesPaint(3, Color.BLUE);
        render.setSeriesPaint(4, Color.CYAN);
        render.setBaseShapesVisible(true);
        final CategoryAxis cateaxis = plot.getDomainAxis();
        cateaxis.setLabelFont(new Font("Dotum", Font.BOLD, 16));
        cateaxis.setTickLabelFont(new Font("Dotum", Font.BOLD, 16));
        final NumberAxis numaxis = (NumberAxis) plot.getRangeAxis();
        numaxis.setLabelFont(new Font("Dotum", Font.BOLD, 16));
        final TextTitle title = new TextTitle(chartTitle);
        title.setFont(new Font("Dotum", Font.BOLD, 16));
        jfreechart.setTitle(title);
        return jfreechart;
    }

    private static void saveAsFile(JFreeChart chart, String outputPath, int weight, int height) {
        FileOutputStream out = null;
        try {
            java.io.File outFile = new java.io.File(outputPath);
            if (!outFile.getParentFile().exists()) {
                outFile.getParentFile().mkdirs();
            }
            out = new FileOutputStream(outputPath);
            ChartUtilities.writeChartAsJPEG(out, chart, weight, height);
            out.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }

    private static class DataSet {
        final int valueSize;
        final ResultType type;
        final String[] rowKeys;
        final double[][] data;

        private DataSet(final int valueSize, final ResultType type, final double[][] data) {
            this.valueSize = valueSize;
            this.type = type;
            switch (type) {
                case GET:
                    this.rowKeys = ROW_KEYS_GET;
                    break;
                case SET:
                    this.rowKeys = ROW_KEYS_SET;
                    break;
                default:
                    throw new IllegalArgumentException("invalid result type");
            }
            this.data = data;
        }
    }

    public static void main(String[] args) {
        final ChartGenerator chartGen = new ChartGenerator();
        chartGen.prepareResult();
        chartGen.generateThreadsTPSChart();
    }
}

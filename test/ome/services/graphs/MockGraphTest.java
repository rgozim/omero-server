/*
 *   Copyright 2010 Glencoe Software, Inc. All rights reserved.
 *   Use is subject to license terms supplied in LICENSE.txt
 */

package ome.services.graphs;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ome.model.annotations.Annotation;
import ome.model.annotations.AnnotationAnnotationLink;
import ome.model.annotations.BasicAnnotation;
import ome.model.annotations.BooleanAnnotation;
import ome.model.annotations.CommentAnnotation;
import ome.model.annotations.DatasetAnnotationLink;
import ome.model.annotations.DoubleAnnotation;
import ome.model.annotations.FileAnnotation;
import ome.model.annotations.ImageAnnotationLink;
import ome.model.annotations.ListAnnotation;
import ome.model.annotations.LongAnnotation;
import ome.model.annotations.NumericAnnotation;
import ome.model.annotations.ProjectAnnotationLink;
import ome.model.annotations.RoiAnnotationLink;
import ome.model.annotations.TermAnnotation;
import ome.model.annotations.TextAnnotation;
import ome.model.annotations.TimestampAnnotation;
import ome.model.annotations.TypeAnnotation;
import ome.model.annotations.XmlAnnotation;
import ome.model.containers.Dataset;
import ome.model.containers.DatasetImageLink;
import ome.model.containers.Project;
import ome.model.containers.ProjectDatasetLink;
import ome.model.core.Image;
import ome.model.roi.Roi;
import ome.model.roi.Shape;
import ome.security.basic.CurrentDetails;
import ome.system.EventContext;
import ome.system.OmeroContext;
import ome.tools.hibernate.ExtendedMetadata;
import omero.model.Plate;
import omero.model.PlateAnnotationLink;
import omero.model.Reagent;
import omero.model.ReagentAnnotationLink;
import omero.model.Screen;
import omero.model.ScreenAnnotationLink;
import omero.model.ScreenPlateLink;
import omero.model.Well;
import omero.model.WellAnnotationLink;
import omero.model.WellReagentLink;
import omero.model.WellSample;
import omero.model.WellSampleAnnotationLink;

import org.jmock.Mock;
import org.jmock.MockObjectTestCase;
import org.jmock.core.Invocation;
import org.jmock.core.Stub;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.StaticApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class MockGraphTest extends MockObjectTestCase {

    public static class MockCurrentDetails extends CurrentDetails {
        @Override
        public EventContext getCurrentEventContext() {
            return createEventContext(false);
        }
    }

    OmeroContext specXml;

    Mock emMock;

    @BeforeMethod
    public void setup() {
        StaticApplicationContext sac = new StaticApplicationContext();

        ConstructorArgumentValues cav = new ConstructorArgumentValues();
        cav.addGenericArgumentValue(ExtendedMetadata.class);
        RootBeanDefinition mock = new RootBeanDefinition(Mock.class, cav, null);

        RootBeanDefinition em = new RootBeanDefinition();
        em.setFactoryBeanName("mock");
        em.setFactoryMethodName("proxy");

        RootBeanDefinition cd = new RootBeanDefinition(MockCurrentDetails.class);

        sac.registerBeanDefinition("currentDetails", cd);
        sac.registerBeanDefinition("mock", mock);
        sac.registerBeanDefinition("extendedMetadata", em);
        sac.refresh();

        emMock = sac.getBean("mock", Mock.class);
        emMock.expects(atLeastOnce())
                .method("getAnnotationTypes")
                .will(returnValue(new HashSet<Class<?>>(Arrays
                        .<Class<?>> asList(Annotation.class,
                                BasicAnnotation.class,
                                BooleanAnnotation.class,
                                NumericAnnotation.class,
                                DoubleAnnotation.class,
                                LongAnnotation.class,
                                TermAnnotation.class,
                                TimestampAnnotation.class,
                                ListAnnotation.class,
                                TextAnnotation.class,
                                CommentAnnotation.class,
                                ome.model.annotations.TagAnnotation.class,
                                XmlAnnotation.class,
                                TypeAnnotation.class,
                                FileAnnotation.class))));
        specXml = new OmeroContext(
                new String[] { "classpath:ome/services/delete/spec.xml" }, sac);
    }

    //
    // Helpers
    //

    protected Object getOp(GraphEntry de) throws Exception {
        Field field = GraphEntry.class.getDeclaredField("operation");
        field.setAccessible(true);
        return field.get(de);
    }


    protected static EventContext createEventContext(boolean admin) {
        Mock m = new Mock(EventContext.class);
        MockGraphTest t = new MockGraphTest();
        m.expects(t.once()).method("isCurrentUserAdmin").will(t.returnValue(admin));
        m.expects(t.once()).method("getCurrentUserId").will(t.returnValue(1L));
        EventContext ec = (EventContext) m.proxy();
        return ec;
    }


    protected GraphEntry findEntry(BaseGraphSpec spec, String name) {
        Integer idx = null;
        GraphEntry entry = null;
        // Find the right entry for /Image
        idx = null;
        for (int i = 0; i < spec.entries.size(); i++) {
            entry = spec.entries.get(i);
            if (entry.getName().equals(name)) {
                idx = i;
                break;
            }
        }
        assertNotNull(idx);
        return entry;
    }

    static Map<String, Map<String, String>> relationships = new HashMap<String, Map<String, String>>();
    static {

        Map<String, String> values;

        values = new HashMap<String, String>();
        values.put("AnnotationAnnotationLink", "annotationLinks");
        values.put("OriginalFile", "file");
        relationships.put("Annotation", values);

        values = new HashMap<String, String>();
        values.put("Shape", "shapes");
        values.put("RoiAnnotationLink", "annotationLinks");
        relationships.put("Roi", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("RoiAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("ProjectDatasetLink", "datasetLinks");
        values.put("ProjectAnnotationLink", "annotationLinks");
        relationships.put("Project", values);

        values = new HashMap<String, String>();
        values.put("Project", "parent");
        values.put("Annotation", "child");
        relationships.put("ProjectAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("Dataset", "child");
        values.put("Project", "parent");
        relationships.put("ProjectDatasetLink", values);

        values = new HashMap<String, String>();
        values.put("ProjectDatasetLink", "projectLinks");
        values.put("DatasetAnnotationLink", "annotationLinks");
        values.put("DatasetImageLink", "imageLinks");
        relationships.put("Dataset", values);

        values = new HashMap<String, String>();
        values.put("Dataset", "parent");
        values.put("Annotation", "child");
        relationships.put("DatasetAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("Dataset", "parent");
        values.put("Image", "child");
        relationships.put("DatasetImageLink", values);

        values = new HashMap<String, String>();
        values.put("DatasetImageLink", "datasetLinks");
        values.put("ImageAnnotationLink", "annotationLinks");
        values.put("Pixels", "pixels");
        values.put("Roi", "rois");
        values.put("ObjectiveSettings", "objectiveSettings");
        values.put("StageLabel", "stageLabel");
        values.put("ImagingEnvironment", "imagingEnvironment");
        values.put("Experiment", "experiment");
        values.put("Instrument", "instrument");
        relationships.put("Image", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("ImageAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("PixelsOriginalFileMap", "pixelsFileMaps");
        values.put("PlaneInfo", "planeInfo");
        values.put("RenderingDef", "settings");
        values.put("Channel", "channels");
        values.put("Thumbnail", "thumbnails");
        values.put("PixelsAnnotationLink", "annotationLinks");
        relationships.put("Pixels", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("PixelsAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("ChannelAnnotationLink", "annotationLinks");
        values.put("StatsInfo", "statsInfo");
        values.put("LogicalChannel", "logicalChannel");
        relationships.put("Channel", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("ChannelAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("LightSettings", "lightSourceSettings");
        values.put("DetectorSettings", "detectorSettings");
        values.put("LightPath", "lightPath");
        relationships.put("LogicalChannel", values);

        values = new HashMap<String, String>();
        values.put("LightPathEmissionFilterLink", "lightPathEmissionFilterLink");
        relationships.put("LightPath", values);


        values = new HashMap<String, String>();
        values.put("MicrobeamManipulation", "microbeamManipulation");
        relationships.put("LightSettings", values);

        values = new HashMap<String, String>();
        values.put("OriginalFile", "parent");
        relationships.put("PixelsOriginalFileMap", values);

        values = new HashMap<String, String>();
        values.put("PlaneInfoAnnotationLink", "annotationLinks");
        relationships.put("PlaneInfo", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("PlaneInfoAnnotationLink", values);



        // SPW

        values = new HashMap<String, String>();
        values.put("ScreenPlateLink", "plateLinks");
        values.put("ScreenAnnotationLink", "annotationLinks");
        values.put("Reagent", "reagent");
        relationships.put("Screen", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("ScreenAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("Plate", "child");
        relationships.put("ScreenPlateLink", values);

        values = new HashMap<String, String>();
        values.put("ReagentAnnotationLink", "annotationLinks");
        relationships.put("Reagent", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("ReagentAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("Well", "wells");
        values.put("PlateAcquisition", "plateAcquisition");
        values.put("PlateAnnotationLink", "annotationLinks");
        relationships.put("Plate", values);

        values = new HashMap<String, String>();
        values.put("Well", "wells");
        values.put("Annotation", "child");
        relationships.put("PlateAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("PlateAcquisitionAnnotationLink", "annotationLinks");
        relationships.put("PlateAcquisition", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("PlateAcquisitionAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("WellSample", "wellSamples");
        values.put("WellAnnotationLink", "annotationLinks");
        values.put("WellReagentLink", "reagentLinks");
        relationships.put("Well", values);

        values = new HashMap<String, String>();
        values.put("Reagent", "child");
        relationships.put("WellReagentLink", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("WellAnnotationLink", values);

        values = new HashMap<String, String>();
        values.put("WellSampleAnnotationLink", "annotationLinks");
        values.put("Image", "image");
        relationships.put("WellSample", values);

        values = new HashMap<String, String>();
        values.put("Annotation", "child");
        relationships.put("WellSampleAnnotationLink", values);

    }

    protected void prepareGetRelationship() {
        emMock.expects(atLeastOnce()).method("getRelationship").will(new Stub(){

            public StringBuffer describeTo(StringBuffer arg0) {
                arg0.append("calls getRelationship");
                return arg0;
            }

            public Object invoke(Invocation arg0) throws Throwable {
                String k1 = (String) arg0.parameterValues.get(0);
                String k2 = (String) arg0.parameterValues.get(1);
                Map<String, String> v = relationships.get(k1);
                if (v == null) {
                    fail("Unknown rel: " + k1 + "->" + k2);
                }
                return v.get(k2);
            }});
    }

    protected void prepareGetHibernateClass() {
        emMock.expects(atLeastOnce()).method("getHibernateClass").will(new Stub() {

            public StringBuffer describeTo(StringBuffer arg0) {
                arg0.append("return a hibernate class");
                return arg0;
            }

            public Object invoke(Invocation arg0) throws Throwable {
                String name = (String) arg0.parameterValues.get(0);
                if (name.equals("Roi")) {
                    return Roi.class;
                } else if (name.equals("Shape")) {
                    return Shape.class;
                } else if (name.equals("Annotation")) {
                    return Annotation.class;
                } else if (name.equals("RoiAnnotationLink")) {
                    return RoiAnnotationLink.class;
                } else if (name.equals("AnnotationAnnotationLink")) {
                    return AnnotationAnnotationLink.class;
                } else if (name.equals("BooleanAnnotation")) {
                    return BooleanAnnotation.class;
                } else if (name.equals("BooleanAnnotation")) {
                    return BooleanAnnotation.class;
                } else if (name.equals("Project")) {
                    return Project.class;
                } else if (name.equals("ProjectAnnotationLink")) {
                    return ProjectAnnotationLink.class;
                } else if (name.equals("ProjectDatasetLink")) {
                    return ProjectDatasetLink.class;
                } else if (name.equals("Dataset")) {
                    return Dataset.class;
                } else if (name.equals("DatasetAnnotationLink")) {
                    return DatasetAnnotationLink.class;
                } else if (name.equals("DatasetImageLink")) {
                    return DatasetImageLink.class;
                } else if (name.equals("Image")) {
                    return Image.class;
                } else if (name.equals("ImageAnnotationLink")) {
                    return ImageAnnotationLink.class;
                } else if (name.equals("Screen")) {
                    return Screen.class;
                } else if (name.equals("ScreenAnnotationLink")) {
                    return ScreenAnnotationLink.class;
                } else if (name.equals("ScreenPlateLink")) {
                    return ScreenPlateLink.class;
                } else if (name.equals("Plate")) {
                    return Plate.class;
                } else if (name.equals("PlateAnnotationLink")) {
                    return PlateAnnotationLink.class;
                } else if (name.equals("Well")) {
                    return Well.class;
                } else if (name.equals("WellAnnotationLink")) {
                    return WellAnnotationLink.class;
                } else if (name.equals("WellReagentLink")) {
                    return WellReagentLink.class;
                } else if (name.equals("WellSample")) {
                    return WellSample.class;
                } else if (name.equals("WellSampleAnnotationLink")) {
                    return WellSampleAnnotationLink.class;
                } else if (name.equals("Reagent")) {
                    return Reagent.class;
                } else if (name.equals("ReagentAnnotationLink")) {
                    return ReagentAnnotationLink.class;
                } else {
                    fail("Unknown: " + name);
                    return null;
                }
            }
        });
    }

    class GraphQuery {
        final int columns;
        final String query;
        List<List<Long>> table;
        public GraphQuery(int columns, String query) {
            this.columns = columns;
            this.query = query;
        }

        public void add(long...ids) {
            assertEquals(columns, ids.length);
            if (table == null) {
                table = new ArrayList<List<Long>>();
            }
            List<Long> list = new ArrayList<Long>(ids.length);
            for (int i = 0; i < ids.length; i++) {
                list.add(ids[i]);
            }
            table.add(list);
        }

        public void none() {
            table = Collections.unmodifiableList(new ArrayList<List<Long>>());
        }

        public List<List<Long>> table() {
            if (table == null) {
                return new ArrayList<List<Long>>();
            }
            return table;
        }
    }

    class Queries {

        final Map<String, GraphQuery> queries = new HashMap<String, GraphQuery>();

        // Specific annotations
        final GraphQuery annotationWithLinks = query(2, "select ROOT0.id , ROOT1.id from Annotation as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id and (ROOT0.class = Annotation) ");
        final GraphQuery booleanAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = BooleanAnnotation) ");
        final GraphQuery fileAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = FileAnnotation) ");
        final GraphQuery listAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = ListAnnotation) ");
        final GraphQuery xmlAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = XmlAnnotation) ");
        final GraphQuery tagAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = TagAnnotation) ");
        final GraphQuery commentAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = CommentAnnotation) ");
        final GraphQuery longAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = LongAnnotation) ");
        final GraphQuery termAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = TermAnnotation) ");
        final GraphQuery timestampAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = TimestampAnnotation) ");
        final GraphQuery doubleAnnotations = query(1, "select ROOT0.id from Annotation as ROOT0 where ROOT0.id = :id and (ROOT0.class = DoubleAnnotation) ");
        final GraphQuery annotationFile = query(2, "select ROOT0.id , ROOT1.id from Annotation as ROOT0 join ROOT0.file as ROOT1 where ROOT0.id = :id and (ROOT0.class = FileAnnotation) ");

        // SPW
        final GraphQuery screenPlateLinks = query(2, "select ROOT0.id , ROOT1.id from Screen as ROOT0 join ROOT0.plateLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery screenPlates = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Screen as ROOT0 join ROOT0.plateLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery screenAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Screen as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery screenAnnotations = query( 3, "select ROOT0.id , ROOT1.id , ROOT2.id from Screen as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery reagentAnnotationLinks = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Screen as ROOT0 join ROOT0.reagent as ROOT1 join ROOT1.annotationLinks as ROOT2 where ROOT0.id = :id ");
        final GraphQuery reagentAnnotations = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Screen as ROOT0 join ROOT0.reagent as ROOT1 join ROOT1.annotationLinks as ROOT2 join ROOT2.child as ROOT3 where ROOT0.id = :id ");
        final GraphQuery reagents = query(2, "select ROOT0.id , ROOT1.id from Screen as ROOT0 join ROOT0.reagent as ROOT1 where ROOT0.id = :id ");
        final GraphQuery screens = query(1, "select ROOT0.id from Screen as ROOT0 where ROOT0.id = :id ");
        final GraphQuery plateWells = query(2, "select ROOT0.id , ROOT1.id from Plate as ROOT0 join ROOT0.wells as ROOT1 where ROOT0.id = :id ");
        final GraphQuery sampleAnnotationLinks = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Well as ROOT0 join ROOT0.wellSamples as ROOT1 join ROOT1.annotationLinks as ROOT2 where ROOT0.id = :id ");
        final GraphQuery sampleAnnotations = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Well as ROOT0 join ROOT0.wellSamples as ROOT1 join ROOT1.annotationLinks as ROOT2 join ROOT2.child as ROOT3 where ROOT0.id = :id ");
        final GraphQuery samples = query(2, "select ROOT0.id , ROOT1.id from Well as ROOT0 join ROOT0.wellSamples as ROOT1 where ROOT0.id = :id ");
        final GraphQuery wellImages = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Well as ROOT0 join ROOT0.wellSamples as ROOT1 join ROOT1.image as ROOT2 where ROOT0.id = :id ");
        final GraphQuery wellAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Well as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery wellAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Well as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery wellReagentLinks = query(2, "select ROOT0.id , ROOT1.id from Well as ROOT0 join ROOT0.reagentLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery wells = query(1, "select ROOT0.id from Well as ROOT0 where ROOT0.id = :id ");
        final GraphQuery plateAcquisitionAnnotationLinks = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Plate as ROOT0 join ROOT0.plateAcquisition as ROOT1 join ROOT1.annotationLinks as ROOT2 where ROOT0.id = :id ");
        final GraphQuery plateAcquisitionAnnotations = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Plate as ROOT0 join ROOT0.plateAcquisition as ROOT1 join ROOT1.annotationLinks as ROOT2 join ROOT2.child as ROOT3 where ROOT0.id = :id ");
        final GraphQuery plateAcquisitions = query(2, "select ROOT0.id , ROOT1.id from Plate as ROOT0 join ROOT0.plateAcquisition as ROOT1 where ROOT0.id = :id ");
        final GraphQuery plateAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Plate as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery plateAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Plate as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery plates = query(1, "select ROOT0.id from Plate as ROOT0 where ROOT0.id = :id ");
        // Containers
        final GraphQuery projectDatasetLinks = query(2, "select ROOT0.id , ROOT1.id from Project as ROOT0 join ROOT0.datasetLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery projectDatasets = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Project as ROOT0 join ROOT0.datasetLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery datasetImageLinks = query(2, "select ROOT0.id , ROOT1.id from Dataset as ROOT0 join ROOT0.imageLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery datasetImages = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Dataset as ROOT0 join ROOT0.imageLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery projectAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Project as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery projectAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Project as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery projects = query(1, "select ROOT0.id from Project as ROOT0 where ROOT0.id = :id ");
        final GraphQuery datasetAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Dataset as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery datasetAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Dataset as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery datasets = query(1, "select ROOT0.id from Dataset as ROOT0 where ROOT0.id = :id ");
        // Image etc.
        final GraphQuery rois = query(1, "select ROOT0.id from Roi as ROOT0 where ROOT0.id = :id ");
        final GraphQuery roiShapes = query(2, "select ROOT0.id , ROOT1.id from Roi as ROOT0 join ROOT0.shapes as ROOT1 where ROOT0.id = :id ");
        final GraphQuery roiAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Roi as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery roiAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Roi as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery imageDatasetLinks = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.datasetLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery images = query(1, "select ROOT0.id from Image as ROOT0 where ROOT0.id = :id ");
        final GraphQuery imageRois = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.rois as ROOT1 where ROOT0.id = :id ");
        final GraphQuery imageFileMaps = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.pixelsFileMaps as ROOT2 where ROOT0.id = :id ");
        final GraphQuery imageFiles = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.pixelsFileMaps as ROOT2 join ROOT2.parent as ROOT3 where ROOT0.id = :id ");
        final GraphQuery planeInfoAnnotationLinks = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.planeInfo as ROOT2 join ROOT2.annotationLinks as ROOT3 where ROOT0.id = :id ");
        final GraphQuery planeInfoAnnotations = query(5, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id , ROOT4.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.planeInfo as ROOT2 join ROOT2.annotationLinks as ROOT3 join ROOT3.child as ROOT4 where ROOT0.id = :id ");
        final GraphQuery planeInfo = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.planeInfo as ROOT2 where ROOT0.id = :id ");
        final GraphQuery imageRdefs = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.settings as ROOT2 where ROOT0.id = :id ");
        final GraphQuery imageChannels = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 where ROOT0.id = :id ");
        final GraphQuery imageThumbnails = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.thumbnails as ROOT2 where ROOT0.id = :id ");
        final GraphQuery pixelAnnotationLinks = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.annotationLinks as ROOT2 where ROOT0.id = :id ");
        final GraphQuery pixelAnnotations = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.annotationLinks as ROOT2 join ROOT2.child as ROOT3 where ROOT0.id = :id ");
        final GraphQuery pixels = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.pixels as ROOT1 where ROOT0.id = :id ");
        final GraphQuery imageAnnotationLinks = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.annotationLinks as ROOT1 where ROOT0.id = :id ");
        final GraphQuery imageAnnotations = query(3, "select ROOT0.id , ROOT1.id , ROOT2.id from Image as ROOT0 join ROOT0.annotationLinks as ROOT1 join ROOT1.child as ROOT2 where ROOT0.id = :id ");
        final GraphQuery channelAnnotationLinks = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.annotationLinks as ROOT3 where ROOT0.id = :id ");
        final GraphQuery channelAnnotations = query(5, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id , ROOT4.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.annotationLinks as ROOT3 join ROOT3.child as ROOT4 where ROOT0.id = :id ");
        final GraphQuery statsInfo = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.statsInfo as ROOT3 where ROOT0.id = :id ");
        final GraphQuery logicalChannel = query(4, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.logicalChannel as ROOT3 where ROOT0.id = :id ");
        final GraphQuery lightSourceSettings = query(5, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id , ROOT4.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.logicalChannel as ROOT3 join ROOT3.lightSourceSettings as ROOT4 where ROOT0.id = :id ");
        final GraphQuery lightSourceSettingsMicroBeam = query(6, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id , ROOT4.id , ROOT5.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.logicalChannel as ROOT3 join ROOT3.lightSourceSettings as ROOT4 join ROOT4.microbeamManipulation as ROOT5 where ROOT0.id = :id ");
        final GraphQuery detectorSettings = query(5, "select ROOT0.id , ROOT1.id , ROOT2.id , ROOT3.id , ROOT4.id from Image as ROOT0 join ROOT0.pixels as ROOT1 join ROOT1.channels as ROOT2 join ROOT2.logicalChannel as ROOT3 join ROOT3.detectorSettings as ROOT4 where ROOT0.id = :id ");
        final GraphQuery objectiveSettings = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.objectiveSettings as ROOT1 where ROOT0.id = :id ");
        final GraphQuery environment = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.imagingEnvironment as ROOT1 where ROOT0.id = :id ");
        final GraphQuery stageLabel = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.stageLabel as ROOT1 where ROOT0.id = :id ");
        final GraphQuery experiment = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.experiment as ROOT1 where ROOT0.id = :id ");
        final GraphQuery instrument = query(2, "select ROOT0.id , ROOT1.id from Image as ROOT0 join ROOT0.instrument as ROOT1 where ROOT0.id = :id ");


        public GraphQuery get(String str) {
            return queries.get(str);
        }

        public void roiWithAnnotation(long roi, long link, long ann, GraphQuery anns) {
            rois.add(roi);
            roiAnnotationLinks.add(roi, link);
            roiAnnotations.add(roi, link, ann);
            anns.add(ann);
        }

        //
        // HELPERS
        //

        private List<List<Long>> lastColumns(int numberOfColumns, List<List<Long>> rows) {
            List<List<Long>> rv = new ArrayList<List<Long>>();
            for (List<Long> cols : rows) {
                assertTrue(numberOfColumns <= cols.size());
                List<Long> rv2 = new ArrayList<Long>();
                for (int i = cols.size() - numberOfColumns; i < cols.size(); i++) {
                    rv2.add(cols.get(i));
                }
                rv.add(rv2);
            }
            return rv;
        }


        private GraphQuery query(int columns, String query) {
            GraphQuery q = new GraphQuery(columns, query);
            queries.put(query, q);
            return q;
        }


    }

}

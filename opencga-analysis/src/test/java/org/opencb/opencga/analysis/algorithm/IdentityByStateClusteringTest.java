package org.opencb.opencga.analysis.algorithm;

import org.junit.Test;
import org.opencb.biodata.formats.variant.vcf4.io.VariantVcfReader;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by jmmut on 2015-11-13.
 *
 * @author Jose Miguel Mut Lopez &lt;jmmut@ebi.ac.uk&gt;
 */
public class IdentityByStateClusteringTest {

    @Test
    public void testIBSByRegion() throws Exception {
        String fileName = "ibs.vcf";
        VariantSource source = new VariantSource(fileName, "fid", "sid", "studyName");
        String line;

        VariantVcfReader variantReader = new VariantVcfReader(source, IdentityByStateClusteringTest.class.getClassLoader().getResource(source.getFileName()).getPath());
        variantReader.open();
        variantReader.pre();
        List<Variant> variants = variantReader.read(50);
        variantReader.post();
        variantReader.close();

        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        List<String> samples = new ArrayList<>(variants.get(0).getStudy(source.getStudyId()).getSamplesName());
        IdentityByState[] ibsesFirstHalf = ibsc.countIBS(variants.subList(0, variants.size()/2), samples);
        IdentityByState[] ibsesSecondHalf = ibsc.countIBS(variants.subList(variants.size()/2, variants.size()), samples);

        for (int i = 0; i < ibsesFirstHalf.length; i++) {
            ibsesFirstHalf[i].add(ibsesSecondHalf[i]);
        }

        InputStream inputStream = IdentityByStateClusteringTest.class.getClassLoader().getResourceAsStream("ibs.genome");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        reader.readLine();  // ignore header

        assertIBS(ibsc, ibsesFirstHalf, reader);
    }

    @Test
    public void testCountIBS() throws Exception {
        String fileName = "ibs.vcf";
        VariantSource source = new VariantSource(fileName, "fid", "sid", "studyName");
        String line;

        VariantVcfReader variantReader = new VariantVcfReader(source, IdentityByStateClusteringTest.class.getClassLoader().getResource(source.getFileName()).getPath());
        variantReader.open();
        variantReader.pre();
        List<Variant> variants = variantReader.read(50);
        variantReader.post();
        variantReader.close();

        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        List<String> samples = new ArrayList<>(variants.get(0).getStudy(source.getStudyId()).getSamplesName());
        IdentityByState[] ibses = ibsc.countIBS(variants, samples);


        InputStream inputStream = IdentityByStateClusteringTest.class.getClassLoader().getResourceAsStream("ibs.genome");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        reader.readLine();  // ignore header

        assertIBS(ibsc, ibses, reader);
    }

    private void assertIBS(IdentityByStateClustering ibsc, IdentityByState[] ibsesFirstHalf, BufferedReader reader) throws IOException {
        String line = reader.readLine();
        while (line != null) {
            String[] split = line.split("\t");
            int fileFirst = Integer.parseInt(split[0]);
            int fileSecond = Integer.parseInt(split[2]);
            int index = ibsc.getCompoundIndex(fileFirst, fileSecond);
            IdentityByState ibs = ibsesFirstHalf[index];
            String message = "elem " + fileFirst + ", " + fileSecond + " " + ibs.toString();
            assertEquals(message, Integer.parseInt(split[14]), ibs.ibs[0]);
            assertEquals(message, Integer.parseInt(split[15]), ibs.ibs[1]);
            assertEquals(message, Integer.parseInt(split[16]), ibs.ibs[2]);
            assertEquals(message, Float.parseFloat(split[11]), ibsc.getDistance(ibs), 0.0001);
            line = reader.readLine();
        }
    }

    @Test
    public void testCountSharedAlleles() throws Exception {
        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("0/0"), new Genotype("0/0")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("0/0"), new Genotype("0/1")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("0/0"), new Genotype("1/0")));
        assertEquals(0, ibsc.countSharedAlleles(2, new Genotype("0/0"), new Genotype("1/1")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("0/1"), new Genotype("0/0")));
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("0/1"), new Genotype("0/1")));
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("0/1"), new Genotype("1/0")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("0/1"), new Genotype("1/1")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("1/0"), new Genotype("0/0")));
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("1/0"), new Genotype("0/1")));
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("1/0"), new Genotype("1/0")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("1/0"), new Genotype("1/1")));
        assertEquals(0, ibsc.countSharedAlleles(2, new Genotype("1/1"), new Genotype("0/0")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("1/1"), new Genotype("0/1")));
        assertEquals(1, ibsc.countSharedAlleles(2, new Genotype("1/1"), new Genotype("1/0")));
        assertEquals(2, ibsc.countSharedAlleles(2, new Genotype("1/1"), new Genotype("1/1")));
    }

    @Test
    public void testGetCompountIndex() throws Exception {
        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        assertEquals(0, ibsc.getCompoundIndex(0, 1));
        assertEquals(1, ibsc.getCompoundIndex(0, 2));
        assertEquals(2, ibsc.getCompoundIndex(1, 2));
        assertEquals(3, ibsc.getCompoundIndex(0, 3));
        assertEquals(6, ibsc.getCompoundIndex(0, 4));
        assertEquals(10, ibsc.getCompoundIndex(0, 5));
    }

    @Test
    public void testGetSecondSampleIndex() throws Exception {
        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        int n = 10000;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                assertEquals(j, ibsc.getSecondSampleIndex(ibsc.getCompoundIndex(i, j)));
            }
        }
    }

    @Test
    public void testGetFirstSampleIndex() throws Exception {
        IdentityByStateClustering ibsc = new IdentityByStateClustering();
        int n = 10000;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                assertEquals(i, ibsc.getFirstSampleIndex(ibsc.getCompoundIndex(i, j), j));
            }
        }
    }
}
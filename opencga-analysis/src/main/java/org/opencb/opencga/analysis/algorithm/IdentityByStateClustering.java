package org.opencb.opencga.analysis.algorithm;

import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;

import java.util.*;

/**
 * Created by jmmut on 2015-11-13.
 *
 * assumptions:
 * - samples.size() > 1 && samples.size() < 10000
 * - only one study
 * - only 2 alleles per genotype
 * 
 * @author Jose Miguel Mut Lopez &lt;jmmut@ebi.ac.uk&gt;
 */
public class IdentityByStateClustering {

    /**
     * @return an array of IBS of length: (samples.size()*(samples.size() -1))/2
     * which is samples.size() choose 2
     */
    IdentityByState[] countIBS(List<Variant> variants, List<String> samples) {
        return countIBS(variants.iterator(), samples);
    }
    
    /**
     * @return an array of IBS of length: (samples.size()*(samples.size() -1))/2
     * which is samples.size() choose 2
     */
    IdentityByState[] countIBS(Variant variant, List<String> samples) {
        return countIBS(Collections.singletonList(variant).iterator(), samples);
    }

    /**
     * @return an array of IBS of length: (samples.size()*(samples.size() -1))/2
     * which is samples.size() choose 2
     */
    IdentityByState[] countIBS(Iterator<Variant> iterator, List<String> samples) {
        
        // assumptions
        if (samples.size() < 1 || samples.size() > 10000) {
            throw new IllegalArgumentException("samples.size() is " + samples.size()
                    + " and it should be between 1 and 10000");
        }
        final int studyIndex = 0;
        final int allelesCount = 2;


        // loops
        IdentityByState[] counts = new IdentityByState[getAmountOfPairs(samples)];

        for (int i = 0; i < counts.length; i++) {
            counts[i] = new IdentityByState();
        }

        for (; iterator.hasNext(); ) {
            Variant variant = iterator.next();
            for (int i = 1; i < samples.size(); i++) {
                for (int j = 0; j < i; j++) {
                    StudyEntry studyEntry = variant.getStudies().get(studyIndex);
                    String gtI = studyEntry.getSampleData(samples.get(i), "GT");
                    String gtJ = studyEntry.getSampleData(samples.get(j), "GT");
                    Genotype genotypeI = new Genotype(gtI);
                    Genotype genotypeJ = new Genotype(gtJ);
                    
                    int whichIBS = countSharedAlleles(allelesCount, genotypeI, genotypeJ);
                    counts[getCompoundIndex(j, i)].ibs[whichIBS]++;
                }
            }
        }
        return counts;
    }

    /**
     * Counts the amount of shared alleles in two individuals.
     * This is which IBS kind is this pair: IBS0, IBS1 or IBS2.
     * The idea is to count how many alleles there are of each kind: for instance, 0 reference alleles for individual 1 
     * and 2 reference alleles for individual 2, and then count the alternate alleles. Then take the minimum of each 
     * kind and sum all the minimums.
     * @param allelesCount ploidy
     * @param genotypeFirst first individual's genotype
     * @param genotypeSecond second individual's genotype
     * @return shared alleles count.
     */
    public int countSharedAlleles(int allelesCount, Genotype genotypeFirst, Genotype genotypeSecond) {
        
        // amount of different alleles: reference, alternate. other alleles (missing, or multiallelic) are ignored
        int[] allelesCountsFirst = new int[2];
        int[] allelesCountsSecond = new int[2];
        
        for (int k = 0; k < allelesCount; k++) {
            if (genotypeFirst.getAllele(k) == 0) {
                allelesCountsFirst[0]++;
            } else if (genotypeFirst.getAllele(k) == 1) {
                allelesCountsFirst[1]++;
            }
            if (genotypeSecond.getAllele(k) == 0) {
                allelesCountsSecond[0]++;
            } else if (genotypeSecond.getAllele(k) == 1) {
                allelesCountsSecond[1]++;
            }
        }
        
        int whichIBS = Math.min(allelesCountsFirst[0], allelesCountsSecond[0]) 
                + Math.min(allelesCountsFirst[1], allelesCountsSecond[1]);
        
        return whichIBS;
    }

    public double getDistance(IdentityByState counts) {
        return (counts.ibs[1]*0.5 + counts.ibs[2])/(counts.ibs[0]+counts.ibs[1]+ counts.ibs[2]);
    }
    
    public int getAmountOfPairs(List<String> samples) {
        return getCompoundIndex(samples.size()-2, samples.size()-1) +1;
    }

    /**
     *     j
     *    /_0__1__2__3__4_
     * i 0| -  0  1  3  6 |
     *   1|    -  2  4  7 |
     *   2|       -  5  8 |
     *   3|          -  9 |
     *   4|             - |
     *  
     *  `(j*(j-1)) / 2` is the amount of numbers in the triangular matrix before the column `j`.
     *  
     *  for example, with i=2, j=4:
     *  (j*(j-1)) / 2 == 6;
     *  6+i == 8
     */
    public int getCompoundIndex(int first, int second) {
        if (first >= second) {
            throw new IllegalArgumentException("first (" + first + ") and second (" + second 
                    + ") sample indexes, must comply with: 0 <= first < second");
        }
        return (second*second - second)/2 + first;
        
    }

    /**
     *
     
     n = (j^2 - j)/2 + i
     0 <= i < j
     
     replacing i as j:
     
     n >= (j^2 - j/2 + 0
     
     n < (j^2 + j)/2

     2*n < j^2 + j

     2*n + 1/4 < j^2 + j + 1/4

     2*n + 1/4 < (j + 1/2)^2 
     
     sqrt(2*n + 1/4) - 1/2 < j
     
     similarly, from 
     
     n = (j^2 - j)/2 + i
     0 <= i < j
     
     replacing i as 0, gives
     
     sqrt(2*n + 1/4) + 1/2 >= j
     
     so we have both boundaries
     
     sqrt(2*n + 1/4) - 1/2 < j <= sqrt(2*n + 1/4) + 1/2
     
     lets rename this as
     
     a < j <= b
     
     as j is integer, and a + 1 = b, and a is strictly less than j, floor(b) will always be j.
     */
    public int getSecondSampleIndex(int compoundIndex) {
        return (int) Math.round(Math.floor(Math.sqrt(2 * compoundIndex + 0.25) + 0.5));
    }

    /**
     * n = (j^2 - j)/2 + i
     */
    public int getFirstSampleIndex(int compoundIndex, int secondSampleIndex) {
        return compoundIndex - (secondSampleIndex * secondSampleIndex - secondSampleIndex) / 2;
    }
}


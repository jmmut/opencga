package org.opencb.opencga.analysis.algorithm;

import java.util.Arrays;

/**
 * Created by jmmut on 2015-11-20.
 *
 * @author Jose Miguel Mut Lopez &lt;jmmut@ebi.ac.uk&gt;
 */
public class IdentityByState {
    public int[] ibs = {0, 0, 0};

//        public IBS() {
//        }

    public void add(IdentityByState param) {
        for (int i = 0; i < ibs.length; i++) {
            ibs[i] += param.ibs[i];
        }
    }

    @Override
    public String toString() {
        return "IBS{" +
                "ibs=" + Arrays.toString(ibs) +
                '}';
    }
}

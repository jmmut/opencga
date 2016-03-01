/*
 * Copyright 2016 OpenCB
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

package org.opencb.opencga.storage.hadoop.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.mapreduce.TableInputFormat;
import org.opencb.biodata.tools.variant.converter.Converter;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.hpg.bigdata.core.connectors.Connector;
import org.opencb.opencga.storage.core.variant.StudyConfigurationManager;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.HBaseStudyConfigurationManager;
import org.opencb.opencga.storage.hadoop.variant.index.HBaseToVariantConverter;

/**
 * Created by jmmut on 2016-02-19.
 *
 * @author Jose Miguel Mut Lopez &lt;jmmut@ebi.ac.uk&gt;
 */
public class HBaseConnector implements Connector {
    private String tableName;

    public HBaseConnector(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public ObjectMap getCredentials() {
        return null;
    }

    @Override
    public Converter getConverter() throws Exception {
        Configuration conf = HBaseConfiguration.create();
        conf.set(TableInputFormat.INPUT_TABLE, tableName);
        GenomeHelper genomeHelper = new GenomeHelper(conf);
        StudyConfigurationManager scm = new HBaseStudyConfigurationManager(tableName, conf, null);
        return new HBaseToVariantConverter(genomeHelper, scm);
    }
}

/*
 * Copyright 2015 OpenCB
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

package org.opencb.opencga.storage.app.cli;

/**
 * Created by imedina on 25/05/15.
 */
public class CreateAccessionsCommandExecutor extends CommandExecutor {

    private CliOptionsParser.CreateAccessionsCommandOption createAccessionsCommandOption;


    public CreateAccessionsCommandExecutor(CliOptionsParser.CreateAccessionsCommandOption createAccessionsCommandOption) {
        super(createAccessionsCommandOption.logLevel, createAccessionsCommandOption.verbose,
                createAccessionsCommandOption.configFile);

        this.createAccessionsCommandOption = createAccessionsCommandOption;
    }


    @Override
    public void execute() throws Exception {
        logger.info("Starting execution");
    }
}

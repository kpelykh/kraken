/*
 * Copyright 2010 NCHOVY
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.krakenapps.sqlparser.parser;

import org.krakenapps.bnf.Binding;
import org.krakenapps.bnf.Parser;
import org.krakenapps.sqlparser.ast.InsertStatement;
import org.krakenapps.sqlparser.ast.InsertValues;

public class InsertStatementParser implements Parser {

	@Override
	public Object parse(Binding b) {
		Binding[] children = b.getChildren();
		String tableName = (String) children[2].getValue();
		InsertValues values = (InsertValues) children[3].getValue();
		return new InsertStatement(tableName, values);
	}

}

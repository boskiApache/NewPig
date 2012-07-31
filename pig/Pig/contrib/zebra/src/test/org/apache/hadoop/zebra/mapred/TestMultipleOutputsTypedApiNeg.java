/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.zebra.mapred;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import junit.framework.Assert;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.lib.MultipleOutputs;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.zebra.BaseTestCase;
import org.apache.hadoop.zebra.mapred.BasicTableOutputFormat;
import org.apache.hadoop.zebra.mapred.TestBasicTableIOFormatLocalFS.InvIndex;
import org.apache.hadoop.zebra.parser.ParseException;
import org.apache.hadoop.zebra.schema.Schema;
import org.apache.hadoop.zebra.types.TypesUtils;
import org.apache.hadoop.zebra.types.ZebraTuple;
import org.apache.pig.ExecType;
import org.apache.pig.PigServer;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.DefaultTuple;
import org.apache.pig.data.Tuple;
import org.apache.pig.test.MiniCluster;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This is a sample a complete MR sample code for Table. It doens't contain
 * 'read' part. But, it should be similar and easier to write. Refer to test
 * cases in the same directory.
 * 
 * Assume the input files contain rows of word and count, separated by a space:
 * 
 * <pre>
 * us 2
 * japan 2
 * india 4
 * us 2
 * japan 1
 * india 3
 * nouse 5
 * nowhere 4
 * 
 * 
 */
public class TestMultipleOutputsTypedApiNeg extends BaseTestCase implements Tool {
  static String inputPath;
  static String inputFileName = "multi-input.txt";
  public static String sortKey = null;

  private static String strTable1 = null;
  private static String strTable2 = null;
  private static String strTable3 = null;

  @BeforeClass
  public static void setUpOnce() throws Exception {
	  init();
    // set inputPath and output path
    String workingDir = null;
    inputPath = getTableFullPath(inputFileName).toString();
    writeToFile(inputPath);
  }

  public static void writeToFile(String inputFile) throws IOException {
    if ( mode ==  TestMode.local ) {
      FileWriter fstream = new FileWriter(inputFile);
      BufferedWriter out = new BufferedWriter(fstream);
      out.write("us 2\n");
      out.write("japan 2\n");
      out.write("india 4\n");
      out.write("us 2\n");
      out.write("japan 1\n");
      out.write("india 3\n");
      out.write("nouse 5\n");
      out.write("nowhere 4\n");
      out.close();
    } else {
      FSDataOutputStream fout = fs.create(new Path(inputFile));
      fout.writeBytes("us 2\n");
      fout.writeBytes("japan 2\n");
      fout.writeBytes("india 4\n");
      fout.writeBytes("us 2\n");
      fout.writeBytes("japan 1\n");
      fout.writeBytes("india 3\n");
      fout.writeBytes("nouse 5\n");
      fout.writeBytes("nowhere 4\n");
      fout.close();
    }
  }

  public String getCurrentMethodName() {
	  ByteArrayOutputStream baos = new ByteArrayOutputStream();
	  PrintWriter pw = new PrintWriter(baos);
	  (new Throwable()).printStackTrace(pw);
	  pw.flush();
	  String stackTrace = baos.toString();
	  pw.close();

	  StringTokenizer tok = new StringTokenizer(stackTrace, "\n");
	  tok.nextToken(); // 'java.lang.Throwable'
	  tok.nextToken(); // 'at ...getCurrentMethodName'
	  String l = tok.nextToken(); // 'at ...<caller to getCurrentRoutine>'
	  // Parse line 3
	  tok = new StringTokenizer(l.trim(), " <(");
	  String t = tok.nextToken(); // 'at'
	  t = tok.nextToken(); // '...<caller to getCurrentRoutine>'
	  StringTokenizer st = new StringTokenizer(t, ".");
	  String methodName = null;
	  while (st.hasMoreTokens()) {
		  methodName = st.nextToken();
	  }
	  return methodName;
  }
  
  public static void getTablePaths(String myMultiLocs) {
    StringTokenizer st = new StringTokenizer(myMultiLocs, ",");

    // get how many tokens inside st object
    System.out.println("tokens count: " + st.countTokens());
    int count = 0;

    // iterate st object to get more tokens from it
    while (st.hasMoreElements()) {
      count++;
      String token = st.nextElement().toString();
      if ( mode == TestMode.local ) {
        System.out.println("in mini, token: " + token);
        // in mini, token:
        // file:/homes/<uid>/grid/multipleoutput/pig-table/contrib/zebra/ustest3
        if (count == 1)
          strTable1 = token;
        if (count == 2)
          strTable2 = token;
        if (count == 3)
          strTable3 = token;
      } else {
        System.out.println("in real, token: " + token);
        // in real, token: /user/hadoopqa/ustest3
        // note: no prefix file: in real cluster
        if (count == 1)
          strTable1 = token;
        if (count == 2)
          strTable2 = token;
        if (count == 3)
          strTable3 = token;
      }

    }
  }

  public static void checkTable(String myMultiLocs) throws IOException {
    System.out.println("myMultiLocs:" + myMultiLocs);
    System.out.println("sorgetTablePathst key:" + sortKey);

    getTablePaths(myMultiLocs);
    String query1 = null;
    String query2 = null;

    if (strTable1 != null) {

      query1 = "records1 = LOAD '" + strTable1
          + "' USING org.apache.hadoop.zebra.pig.TableLoader();";
    }
    if (strTable2 != null) {
      query2 = "records2 = LOAD '" + strTable2
          + "' USING org.apache.hadoop.zebra.pig.TableLoader();";
    }

    int count1 = 0;
    int count2 = 0;

    if (query1 != null) {
      System.out.println(query1);
      pigServer.registerQuery(query1);
      Iterator<Tuple> it = pigServer.openIterator("records1");
      while (it.hasNext()) {
        count1++;
        Tuple RowValue = it.next();
        System.out.println(RowValue);
        // test 1 us table
        if (query1.contains("test1") || query1.contains("test2")
            || query1.contains("test3")) {

          if (count1 == 1) {
            Assert.assertEquals("us", RowValue.get(0));
            Assert.assertEquals(2, RowValue.get(1));
          }
          if (count1 == 2) {
            Assert.assertEquals("us", RowValue.get(0));
            Assert.assertEquals(2, RowValue.get(1));
          }
        } // test1, test2

      }// while
      if (query1.contains("test1") || query1.contains("test2")
          || query1.contains("test3")) {
        Assert.assertEquals(2, count1);
      }
    }// if query1 != null

    if (query2 != null) {
      pigServer.registerQuery(query2);
      Iterator<Tuple> it = pigServer.openIterator("records2");

      while (it.hasNext()) {
        count2++;
        Tuple RowValue = it.next();
        System.out.println(RowValue);

        // if test1 other table
        if (query2.contains("test1")) {
          if (count2 == 1) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(3, RowValue.get(1));
          }
          if (count2 == 2) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
          if (count2 == 3) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(1, RowValue.get(1));
          }
          if (count2 == 4) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(2, RowValue.get(1));
          }

          if (count1 == 5) {
            Assert.assertEquals("nouse", RowValue.get(0));
            Assert.assertEquals(5, RowValue.get(1));
          }
          if (count1 == 6) {
            Assert.assertEquals("nowhere", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
        }// if test1
        // if test2 other table
        if (query2.contains("test2")) {
          if (count2 == 1) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
          if (count2 == 2) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(3, RowValue.get(1));
          }
          if (count2 == 3) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(2, RowValue.get(1));
          }
          if (count2 == 4) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(1, RowValue.get(1));
          }

          if (count1 == 5) {
            Assert.assertEquals("nouse", RowValue.get(0));
            Assert.assertEquals(5, RowValue.get(1));
          }
          if (count1 == 6) {
            Assert.assertEquals("nowhere", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
        }// if test2
        // if test3 other table
        if (query2.contains("test3")) {
          if (count2 == 1) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(1, RowValue.get(1));
          }
          if (count2 == 2) {
            Assert.assertEquals("japan", RowValue.get(0));
            Assert.assertEquals(2, RowValue.get(1));
          }
          if (count2 == 3) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(3, RowValue.get(1));
          }
          if (count2 == 4) {
            Assert.assertEquals("india", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
          if (count1 == 5) {
            Assert.assertEquals("nowhere", RowValue.get(0));
            Assert.assertEquals(4, RowValue.get(1));
          }
          if (count1 == 6) {
            Assert.assertEquals("nouse", RowValue.get(0));
            Assert.assertEquals(5, RowValue.get(1));
          }

        }// if test3

      }// while
      if (query2.contains("test1") || query2.contains("test2")
          || query2.contains("test3")) {
        Assert.assertEquals(6, count2);
      }
    }// if query2 != null

  }

  @Test(expected = IllegalArgumentException.class)
  public void test1() throws ParseException, IOException,
      org.apache.hadoop.zebra.parser.ParseException, Exception {
    /*
     * path list have an empty value in the middle should throw
     * illegalArgumentExcepiton: Can not create a path from an empty string
     */
    System.out.println("******Start  testcase: " + getCurrentMethodName());
    sortKey = "word,count";
    System.out.println("hello sort on word and count");
    String methodName = getCurrentMethodName();
    String myMultiLocs = null;
    List<Path> paths = new ArrayList<Path>(3);

    myMultiLocs = getTableFullPath( "a" + methodName  ).toString() + "," + 
        getTableFullPath( "b" + methodName ).toString();


    paths.add(getTableFullPath( "a" + methodName  ));
    
    if (mode == TestMode.cluster) {
      try {
        paths.add(new Path(""));
      } catch (IllegalArgumentException e) {
        System.out.println(e.getMessage());
        return;
      }
    } else {
      paths.add(new Path(""));
    }

    // should not reach here
    Assert.fail("Should have seen exception already");

    paths.add(getTableFullPath( "b" + methodName  ));
    getTablePaths(myMultiLocs);
    removeDir(new Path(strTable1));
    removeDir(new Path(strTable2));
    removeDir(new Path(strTable3));
    runMR(sortKey, paths.toArray(new Path[3]));
    System.out.println("DONE test 1");
  }

  @Test(expected = IOException.class)
  public void test2() throws ParseException, IOException,
      org.apache.hadoop.zebra.parser.ParseException, Exception {
    /*
     * path list have only one element OutputPartitionClass return 0 and return
     * 1, expecting more element in the path list
     */
    System.out.println("******Start  testcase: " + getCurrentMethodName());
    sortKey = "word,count";
    System.out.println("hello sort on word and count");
    String methodName = getCurrentMethodName();
    String myMultiLocs = null;
    List<Path> paths = new ArrayList<Path>(1);
    
    Path path = getTableFullPath( "a" + methodName  );
    myMultiLocs = path.toString();
    paths.add( path );

    getTablePaths(myMultiLocs);
    removeDir(new Path(strTable1));
    runMR(sortKey, paths.toArray(new Path[1]));
    System.out.println("DONE test 2");
  }

  @Test(expected = NullPointerException.class)
  public void test3() throws ParseException, IOException,
      org.apache.hadoop.zebra.parser.ParseException, Exception {
    /*
     * path list is null
     */
    System.out.println("******Start  testcase: " + getCurrentMethodName());
    sortKey = "word,count";
    System.out.println("hello sort on word and count");
    String methodName = getCurrentMethodName();
    String myMultiLocs = null;
    List<Path> paths = new ArrayList<Path>(1);

    myMultiLocs = getTableFullPath( "a" + methodName ).toString();
    paths.add(null);
    
    getTablePaths(myMultiLocs);
    removeDir(new Path(strTable1));
    
    if (mode == TestMode.cluster) {
      try {
        runMR(sortKey, paths.toArray(new Path[1]));
      } catch (NullPointerException e) {
        System.err.println(e.getMessage());
        System.out.println("DONE test 3");
        return;
      }
    } else {
      runMR(sortKey, paths.toArray(new Path[1]));
      System.out.println("DONE test 3");
    }
  }

  @Test(expected = IOException.class)
  public void test4() throws ParseException, IOException,
      org.apache.hadoop.zebra.parser.ParseException, Exception {
    /*
     * path list have repeat element, for example atest1 dir has been already
     * created. should throw IOExcepiton. complaining atest4/CG0/.meta already
     * exists
     */
    System.out.println("******Start  testcase: " + getCurrentMethodName());
    sortKey = "word,count";
    System.out.println("hello sort on word and count");
    String methodName = getCurrentMethodName();
    String myMultiLocs = null;
    List<Path> paths = new ArrayList<Path>(3);

    Path pathA = getTableFullPath( "a" + methodName  );
    Path pathB = getTableFullPath( "b" + methodName  );

    myMultiLocs = pathA.toString() + "," + pathA.toString() + "," 
    + pathB.toString();

    paths.add( pathA );
    paths.add( pathA );
    paths.add( pathB );
    getTablePaths(myMultiLocs);
    removeDir(new Path(strTable1));
    removeDir(new Path(strTable2));
    removeDir(new Path(strTable3));
    runMR(sortKey, paths.toArray(new Path[3]));
    System.out.println("DONE test 4");
  }

  static class MapClass implements
      Mapper<LongWritable, Text, BytesWritable, Tuple> {
    private BytesWritable bytesKey;
    private Tuple tupleRow;
    private Object javaObj;

    @Override
    public void map(LongWritable key, Text value,
        OutputCollector<BytesWritable, Tuple> output, Reporter reporter)
        throws IOException {
      // value should contain "word count"
      String[] wdct = value.toString().split(" ");
      if (wdct.length != 2) {
        // LOG the error
        return;
      }

      byte[] word = wdct[0].getBytes();
      bytesKey.set(word, 0, word.length);
      System.out.println("word: " + new String(word));
      tupleRow.set(0, new String(word));
      tupleRow.set(1, Integer.parseInt(wdct[1]));
      System.out.println("count:  " + Integer.parseInt(wdct[1]));

      // This key has to be created by user
      /*
       * Tuple userKey = new DefaultTuple(); userKey.append(new String(word));
       * userKey.append(Integer.parseInt(wdct[1]));
       */
      System.out.println("in map, sortkey: " + sortKey);
      Tuple userKey = new DefaultTuple();
      if (sortKey.equalsIgnoreCase("word,count")) {
        userKey.append(new String(word));
        userKey.append(Integer.parseInt(wdct[1]));
      }

      if (sortKey.equalsIgnoreCase("count")) {
        userKey.append(Integer.parseInt(wdct[1]));
      }

      if (sortKey.equalsIgnoreCase("word")) {
        userKey.append(new String(word));
      }

      try {

        /* New M/R Interface */
        /* Converts user key to zebra BytesWritable key */
        /* using sort key expr tree */
        /* Returns a java base object */
        /* Done for each user key */

        bytesKey = BasicTableOutputFormat.getSortKey(javaObj, userKey);
      } catch (Exception e) {

      }

      output.collect(bytesKey, tupleRow);
    }

    @Override
    public void configure(JobConf job) {
      bytesKey = new BytesWritable();
      sortKey = job.get("sortKey");
      try {
        Schema outSchema = BasicTableOutputFormat.getSchema(job);
        tupleRow = TypesUtils.createTuple(outSchema);
        javaObj = BasicTableOutputFormat.getSortKeyGenerator(job);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (org.apache.hadoop.zebra.parser.ParseException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws IOException {
      // no-op
    }
  }

  static class ReduceClass implements
      Reducer<BytesWritable, Tuple, BytesWritable, Tuple> {
    Tuple outRow;

    @Override
    public void configure(JobConf job) {
    }

    @Override
    public void close() throws IOException {
    }

    public void reduce(BytesWritable key, Iterator<Tuple> values,
        OutputCollector<BytesWritable, Tuple> output, Reporter reporter)
        throws IOException {
      try {
        for (; values.hasNext();) {
          output.collect(key, values.next());
        }
      } catch (ExecException e) {
        e.printStackTrace();
      }
    }

  }

  static class OutputPartitionerClass extends ZebraOutputPartition {

    @Override
    public int getOutputPartition(BytesWritable key, Tuple value)
        throws ExecException {

      String reg = null;
      try {
        reg = (String) (value.get(0));
      } catch (Exception e) {
        //
      }

      if (reg.equals("us"))
        return 0;
      else
        return 1;

    }
  }

  public void runMR(String sortKey, Path... paths) throws ParseException,
      IOException, Exception, org.apache.hadoop.zebra.parser.ParseException {

    JobConf jobConf = new JobConf(conf);
    jobConf.setJobName("TestMultipleOutputsTypedApiNeg");
    jobConf.setJarByClass(TestMultipleOutputsTypedApiNeg.class);
    jobConf.set("table.output.tfile.compression", "gz");
    jobConf.set("sortKey", sortKey);
    // input settings
    jobConf.setInputFormat(TextInputFormat.class);
    jobConf.setMapperClass(TestMultipleOutputsTypedApiNeg.MapClass.class);
    jobConf.setMapOutputKeyClass(BytesWritable.class);
    jobConf.setMapOutputValueClass(ZebraTuple.class);
    FileInputFormat.setInputPaths(jobConf, inputPath);

    jobConf.setNumMapTasks(1);

    // output settings

    jobConf.setOutputFormat(BasicTableOutputFormat.class);
    
    String schema = "word:string, count:int";
    String storageHint = "[word];[count]";
    BasicTableOutputFormat.setMultipleOutputs(jobConf,
        TestMultipleOutputsTypedApiNeg.OutputPartitionerClass.class, paths);
    ZebraSchema zSchema = ZebraSchema.createZebraSchema(schema);
    ZebraStorageHint zStorageHint = ZebraStorageHint
        .createZebraStorageHint(storageHint);
    ZebraSortInfo zSortInfo = ZebraSortInfo.createZebraSortInfo(sortKey, null);
    BasicTableOutputFormat.setStorageInfo(jobConf, zSchema, zStorageHint,
        zSortInfo);
    jobConf.setNumReduceTasks(1);
    JobClient.runJob(jobConf);
    BasicTableOutputFormat.close(jobConf);
  }

  @Override
  public int run(String[] args) throws Exception {
    TestMultipleOutputsTypedApiNeg test = new TestMultipleOutputsTypedApiNeg();
    TestMultipleOutputsTypedApiNeg.setUpOnce();

    test.test1();
    
    //TODO: backend exception - will migrate to real cluster later
    //test.test2();
    
    test.test3();
    
    //TODO: backend exception
    //test.test4();
    
    return 0;
  }

  public static void main(String[] args) throws Exception {
    conf = new Configuration();
    
    int res = ToolRunner.run(conf, new TestMultipleOutputsTypedApiNeg(), args);
    
    System.out.println("PASS");
    System.exit(res);
  } 
}

package Parser
import fileHandler._;
import Bitmaps._
import scala.collection.mutable._;
import scala.collection.immutable.HashMap;

/* MISON Simple Parser without speculative loading.
 * Argument: 		
 * queryFieldList				arrays of query fields. i.e. SELECT [a, b, c]... a, b, c 
 * 											will be in queryFieldList
 * filePaths 						arrays of file paths. More then one if the table is split 
 * 											into multiple files
 * DEBUG_STATUS					DEBUG prints
 */

class MISONParser(
    queryFieldsList: ArrayBuffer[String],
    filePaths: ArrayBuffer[String] = new ArrayBuffer[String],
    DEBUG_STATUS: Boolean = false) {

  // ADT to hold calculate and holds levels of necessary nesting for query
  // and string hashing the query fields
  class queryFields(queryFieldsList: ArrayBuffer[String]) {
    var nestingLevels: Int = 0;
    var levelCount: Int = 0;
    var hashFields: HashSet[Int] = null;
    //createHashField().foreach(println);    uncomment this for NumQueriedFieldsTest()
    createHashField();
    var fieldsOrder: scala.collection.immutable.HashMap[String, Int] = createFieldsOrder(queryFieldsList);

    private def createFieldsOrder(queryFieldsList: ArrayBuffer[String]): scala.collection.immutable.HashMap[String, Int] = {
      var order = new scala.collection.immutable.HashMap[String, Int]();
      for (i <- 0 until queryFieldsList.length) {
        order = order + (queryFieldsList(i) -> i);
      }
      return order;
    }

    def createHashField(): ArrayBuffer[Int] = {
      hashFields = new HashSet[Int];
      var numQueryFieldsList = new ArrayBuffer[Int]();
      var splitCharacter: String = ".";
      var hashCode = 0;
      for (fields <- queryFieldsList) {
        var index: Int = fields.indexOf(splitCharacter);
        var localFieldLevels: Int = 0;
        while (index != -1) {
          // Has period
          var subfield: String = fields.substring(0, index - 1);
          hashCode = subfield.hashCode();
          if (!hashFields.contains(hashCode)) {
            hashFields += hashCode;
            if (localFieldLevels < numQueryFieldsList.size) { // update count 
              numQueryFieldsList(localFieldLevels) = numQueryFieldsList(localFieldLevels) + 1;
            } else { // insert count
              numQueryFieldsList.insert(localFieldLevels, 1);
            }
          }
          localFieldLevels += 1;
          index = fields.indexOf(splitCharacter, index + 1);
        }
        hashCode = fields.hashCode();
        if (!hashFields.contains(hashCode)) {
          hashFields += hashCode;
          if (localFieldLevels < numQueryFieldsList.size) { // update count 
            numQueryFieldsList(localFieldLevels) = numQueryFieldsList(localFieldLevels) + 1;
          } else { // insert count
            numQueryFieldsList.insert(localFieldLevels, 1);
          }
        }
        localFieldLevels += 1; //accounts for the last field
        if (localFieldLevels > nestingLevels) {
          nestingLevels = localFieldLevels;
        }
      }
      return numQueryFieldsList;
    }
    // Gets number of query fields per level
    def getNumQueriedFields(): ArrayBuffer[Int] = {
      var numQueryFieldsList = new ArrayBuffer[Int]();
      var splitCharacter: Char = '.';
      for (i <- 0 to nestingLevels) {
        // count number of query fields in level i
        var uniqueQueryFields = new HashSet[String];
        for (e <- queryFieldsList) {
          val split = e.split(splitCharacter);
          if (i < split.size)
            uniqueQueryFields.add(split(i));
        }
        numQueryFieldsList.insert(i, uniqueQueryFields.size);
      }
      return numQueryFieldsList;
    }
  }

  // Constructor: on
  private var queryFieldsInfo: queryFields = new queryFields(queryFieldsList);
  private var fileHandler: fileHandler = new fileHandler();
  private var result: ArrayBuffer[String] = new ArrayBuffer[String];
  private var recordFoundInLine: Int = 0;
  private var currentRecord: String = "";
  private var defaultArrayLayers: Int = 0;
  private var matchingFieldNumber: Int = 0;
  private var bitmaps: Bitmaps = null;
  private var lineRecordValue: String = "";
  private val DEBUG_FLAG = DEBUG_STATUS;
  private var lineOutput: Array[String] = null;
  // Constructor Off

  // Main Function that parse the file and return arrayBuilder of String for result
  def parseQuery(): ArrayBuffer[String] = {
    result.clear();
    for (i <- 0 until filePaths.length) {
      parseFile(filePaths(i));
    }
    return result;
  }

  // Parse one file and add all positive tuples into var result.
  // Return true for success, false for failure
  private def parseFile(filePath: String): Boolean = {
    fileHandler.setNewFilePath(filePath);

    // Go through entire file one line at a time
    while (fileHandler.getNext) {
      initLineParse();
      System.out.println(currentRecord.length);
      val initialColonPos = bitmaps.generateColonPositions(0, currentRecord.length - 1, 0);
      /*
      if (DEBUG_FLAG == true) {
        System.out.println("Printing colonPosition in parseFile");
        for (i <- 0 until initialColonPos.size) {
          System.out.println("Colon Position is " + initialColonPos(i));
        }
        System.out.println("Record is: " + currentRecord);
        for (i <- initialColonPos) {
          System.out.println(currentRecord.charAt(i));
        }
      }
      * 
      */
      System.out.println(initialColonPos.size);
      val queryResult = parseLine(0, "", initialColonPos);
      if (queryResult) {
        var output: String = "";
        for (fields <- lineOutput) {
          output += fields;
        }
        result += output;
      }
      if (DEBUG_FLAG == true) {
        if (queryResult) {
          System.out.println("Record Matches");
        }
        else {
          System.out.println("Record does not match");
        }
      }
    }
    return true;
  }

  // Initialize parameters for line parsing
  private def initLineParse() {
    val stringSplitted = fileHandler.getFileArray;
    currentRecord = fileHandler.getLineString;
    bitmaps = new Bitmaps(
      queryFieldsInfo.nestingLevels,
      defaultArrayLayers,
      stringSplitted);
    if (DEBUG_FLAG == true) {
      System.out.println("CurrentRecord: " + currentRecord);
      /*
      System.out.println("currentRecord is " + currentRecord);
      System.out.println("stringSplitted prints");
      for (i <- stringSplitted) {
        System.out.println(i);
      }
      * */
    }
    matchingFieldNumber = 0;
    lineOutput = new Array[String](queryFieldsInfo.fieldsOrder.size);
    defaultArrayLayers = 0;
  }

  // Parse one record (line) and determine if the record is part of the query.
  // Return true for success, false for failure
  private def parseLine(curLevel: Int, append: String, colonPos: ArrayBuffer[Int]): Boolean = {
    var recordValue: String = "";
    //for (i <- 0 until colonPos.length) {
    for (i <- colonPos.length - 3 to 0 by -1) {
      System.out.println("i is " + i);
      // end pos of field name, no - 1 due to quirks of scala string.substring(startIndex, endIndex)
      //System.out.println(bitmaps);
      var endPos = bitmaps.getStartingBoundary(colonPos(i));
      System.out.println("endPos: " + endPos);
      // start pos of field name
      var startPos = bitmaps.getStartingBoundary(endPos - 1) + 1;
      System.out.println("startPos: " + startPos);     
      // Error Checking, remove for 
      if (DEBUG_FLAG == true) {
        if (endPos == -1 || startPos == -1) {
          //System.out.println("startPos: " + startPos + " endPos: " + endPos);
          System.out.println("This record: " + currentRecord + "\n has no quotes at all");
          return false;
        }
      }

      val currentField = append + currentRecord.substring(startPos, endPos);

      if (DEBUG_FLAG == true) {
        System.out.println("currentField is " + currentField);
      }
      
      if (queryFieldsInfo.hashFields.contains(currentField.hashCode())) {
        var nextChar: Char = currentRecord.charAt(colonPos(i) + 1);
        // Entering another nesting level case
        if (nextChar == '{') {
          System.out.println("Nesting nesting nestin");
          /*
          var newColonPos: ArrayBuffer[Int] =
            bitmaps.generateColonPositions(colonPos(i), colonPos(i + 1), curLevel + 1);
          var newAppend: String = "";
          if (curLevel == 0) {
            newAppend = currentField + '.';
          } else {
            newAppend = append + '.' + currentField;
          }
          if (DEBUG_FLAG == true) {
            System.out.println("Going to the next level and beyond");
          }
          matchingFieldNumber += 1;
          parseLine(curLevel + 1, newAppend, newColonPos);
          * */
        } // Element is an array
        else if (nextChar == '[') {

        } // Field matches. Add the field element into result
        else {
          System.out.println("Match found");
          endPos = bitmaps.getEndingBoundary(colonPos(i));
          startPos = colonPos(i) + 1;
          System.out.println("startPos: " + startPos); 
          System.out.println("endPos: " + endPos);
          if (currentRecord.charAt(startPos) == '\"') {
            // Change startPos and endPos to compensate for extra " character
            startPos = startPos + 1;
            endPos = endPos - 1;
          }
          val fieldValue = currentRecord.substring(startPos, endPos);
          val pos = queryFieldsInfo.fieldsOrder.get(currentField).get;
          System.out.println("fieldValue is " + fieldValue);
          lineOutput(pos) = fieldValue;
          matchingFieldNumber += 1;

        }

        // Check if all fields were matched
        // Might need to reformat the string currentRecord?
        if (matchingFieldNumber == queryFieldsInfo.hashFields.size) {
          return true;
        }
      }
    }
    return false;
  }
}
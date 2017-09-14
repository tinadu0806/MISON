package Bitmaps
import Bits._
import scala.util.control.Breaks._
/* https://www.scala-lang.org/api/current/scala/collection/immutable/Stack.html
 * (Since version 2.11.0) Stack is an inelegant and potentially
 * poorly-performing wrapper around List. Use List instead: 
 * stack push x becomes x :: list; stack.pop is list.tail.
 * 
 * List is immutable, so in order to use .pop() functionality,
 * ListBuffer is used as an alternative.
 */
import scala.collection.mutable.ListBuffer;
import scala.math.ceil;
class Bitmaps (layers: Int, arrayLayers: Int, wordSplit: Vector[String]) {

  // constructor:on
  class mapContainer (layers: Int, arrayLayers: Int)
  {
    var escapeBitset, quoteBitset, colonBitset, commaBitset,
			lbracketBitset, rbracketBitset, arraylbracketBitset, 
			arrayrbracketBitset, structQBitset, strBitset, 
			structCBitset, structLBitset, structRBitset, 
			structCMBitset, structALBBitset, structARBBitset: 
			Bits = new Bits(0);
  	var levels: Array[Bits] = new Array[Bits](layers);
  	var CMlevels: Array[Bits] = new Array[Bits](arrayLayers);
  	for(i <- 0 until layers)
  	  levels(i) = new Bits(0);
  	for(i <- 0 until arrayLayers)
  	  CMlevels(i) = new Bits(0);
  }
  private val B_INT = 32;
  private var word: Vector[String] = wordSplit;
  var map: Array[mapContainer] = new Array[mapContainer](word.size);
  for(i <- 0 until word.size)
    map(i) = new mapContainer(layers, arrayLayers);
  // constructor:off
  
  // methods:on
  def createBitmap: Unit = {
    fillBits();           // phase 1 & 2
    convertToStruct();    // phase 3/(pre)4
    fillColonBits();      // phase 4
  }
  def fillBits(): Unit = {
    var prev: Array[Char] = new Array[Char](2);
    for(j <- 0 until map.size)
    {
      for(i <- 0 until word(j).size)
      {
        val currentChar = word(j)(i);
        currentChar match {
          case '\\' => 
            map(j).escapeBitset.set(i, 1);
          case '\"' => 
            map(j).quoteBitset.set(i, 1);
            if(prev(0) != '\\' || (prev(0) == '\\' && prev(1) == '\\')) 
              map(j).structQBitset.set(i, 1);
          case ':' =>
            map(j).colonBitset.set(i, 1);
          case ',' =>
            map(j).commaBitset.set(i, 1);
          case '{' =>
					  map(j).lbracketBitset.set(i, 1);
				  case '}' =>
					  map(j).rbracketBitset.set(i, 1);
				  case '[' =>
					  map(j).arraylbracketBitset.set(i, 1);
				  case ']' => 
				  	map(j).arrayrbracketBitset.set(i, 1);
				  case _ => // do nothing for others
        }
        prev(1) = prev(0);
        prev(0) = word(j)(i);
      }
    }
  }
  def convertToStruct(): Unit = {
    var n: Int = 0;  // Number of quotes count
    for(j <- 0 until map.size)
    {
      var mQuote: Bits = map(j).structQBitset;
      var mString: Bits = new Bits(0);
      // Loop until the number of quote in mQuote is 0
      for(i <- map(j).structQBitset.count until 0 by -1)
      {
        // Extract and smear the rightmost 1  m = S(mQuote) = mQuote ^ (mQuote - 1)
        val m:Bits = mQuote ^ (mQuote - 1);
        // Extend mstring to the rightmost 1 mString = mString ^| m
        mString = mString ^ m;
        // remove the rightmost 1 mQuote = R(mQuote) = mQuote & (mQuote - 1)
				mQuote &= (mQuote - 1);
				n += 1;
      }
      // Flip mString if necessary given n mod 2 = 1
      if (n % 2 == 1)
			  mString.flip();
			map(j).strBitset = mString;
    }
    for (i <- 0 until map.size) {
		  var strMask = map(i).strBitset;
		  var temp: Bits = new Bits(0);
		  // Colon
		  temp = map(i).colonBitset;
		  temp = temp - (temp & strMask);
		  map(i).structCBitset = temp;
		  // left Brace
		  temp = map(i).lbracketBitset;
		  temp = temp - (temp & strMask);
		  map(i).structLBitset = temp;
		  // right Brace
		  temp = map(i).rbracketBitset;
		  temp = temp - (temp & strMask);
		  map(i).structRBitset = temp;

		  //for support of array fields
		  // comma
		  temp = map(i).commaBitset;
		  temp = temp - (temp & strMask);
		  map(i).structCMBitset = temp;
		  // left bracket
		  temp = map(i).arraylbracketBitset;
		  temp = temp - (temp & strMask);
		  map(i).structALBBitset = temp;
		  // right bracket
		  temp = map(i).arrayrbracketBitset;
		  temp = temp - (temp & strMask);
		  map(i).structARBBitset = temp;
	  }
  }
  def fillColonBits(): Unit = {
    // copy colon bitmap to leveled colon bitmaps
    
    for (i <- 0 until map.size) {
			for (j <- 0 until map(i).levels.size) 
				map(i).levels(j) = map(i).structCBitset;
			//for(j <- 0 until map(i).CMlevels.size) 
			//	map(i).CMlevels(j) = map(i).structCMBitset;
		}
    var mLeft, mRight: Bits = new Bits(0);									// m(left), m(right)
		var mLbit, mRbit: Bits = new Bits(0);									// m(left bit), m(right bit)
		
		//for array support
		//var mCMLeft, mCMRight: Bits = new Bits(0);
		//var mCMLbit, mCMRbit: Bits = new Bits(0);

		val lvls: Int = map(0).levels.size;					// Number of nesting levels
		//val cmlvls: Int = map(0).CMlevels.size;
		
		// ListBuffer: Stack alternative
		var S: ListBuffer[Tuple2[Int,Bits]] = ListBuffer();
		//var SCM: ListBuffer[Tuple2[Int,Int]] = ListBuffer();
		
		for (i <- 0 until map.size)
		{
			mLeft = map(i).lbracketBitset;
			mRight = map(i).rbracketBitset;
			
			//mCMLeft = map(i).arraylbracketBitset;
			//mCMRight = map(i).arrayrbracketBitset;
			do 													// iterate over each right brace
			{
				// extract the rightmost 1
				mRbit = mRight & -mRight.bits;
				mLbit = mLeft & -mLeft.bits;

				while (mLbit.bits != 0 && (mRbit.bits == 0 || mLbit < mRbit))
				{
					// 1 = "j", 2 = mLbit
					S.insert(0, (i, mLbit));
					mLeft = mLeft & (mLeft - 1);				// remove the rightmost 1
					mLbit = mLeft & -mLeft.bits;					// extract the rightmost 1
				}
				if (mRbit.bits != 0)
				{
					val pop = S.remove(0);				// 0 = "j", 1 = mLbit
					val j = pop._1;
					mLbit = pop._2;
					if (0 < S.size && S.size <= lvls)	// clear bits at the upper level
					{
						var flip: Bits = new Bits(0);
						if (i == j)						// nested object is inside the word
						{
							flip = mRbit - mLbit;
							flip.flip();
							map(i).levels(S.size - 1) &= flip;
						}
						else 									// nested object is across multiple words
						{
							map(j).levels(S.size - 1) &= mLbit - 1;
							flip = mRbit - 1;
							flip.flip();
							map(i).levels(S.size - 1) &= flip;
							for (k <- j + 1 until i) {
								map(k).levels(S.size - 1) = new Bits(0);
							}
						}
					}
				}
				mRight &= mRight - 1;						// remove the rightmost 1
			} while (mRbit.bits != 0);
			/*
			//for array support
			do 													// iterate over each right brace
			{
				// extract the rightmost 1
				mCMRbit = mCMRight & -mCMRight;
				mCMLbit = mCMLeft & -mCMLeft;

				while (mCMLbit != 0 && (mCMRbit == 0 || mCMLbit < mCMRbit))
				{
				  // 1 = "j", 2 = mLbit
					S.insert(0, (i, mCMLbit));
					mCMLeft = mCMLeft & (mCMLeft - 1);				// remove the rightmost 1
					mCMLbit = mCMLeft & -mCMLeft;					// extract the rightmost 1
				}
				if (mCMRbit != 0)
				{

					vector<unsigned> pop = SCM.top();				// 0 = "j", 1 = mLbit
					mCMLbit = pop[1];
					SCM.pop();
					//cout << "m(right bit) exists i: " << i << " j: " << pop[0] << endl;
					//cout << "S.size() after pop: " << S.size() << endl;
					if (0 < SCM.size() && SCM.size() <= cmlvls)	// clear bits at the upper level
					{
						bitset<B_INT> flip;
						//cout << "In 0 < |S| <= l" << endl;
						if (i == pop[0])						// nested object is inside the word
						{
							//cout << "in IF" << endl;
							flip = mCMRbit - mCMLbit;
							flip.flip();
							//map[i].levels[S.size()-1] &= static_cast<unsigned> (flip.to_ulong());
							map[i].CMlevels[SCM.size() - 1] &= flip;
						}
						else 									// nested object is across multiple words
						{

							map[pop[0]].CMlevels[SCM.size() - 1] &= mCMLbit - 1;
							//cout << "map[pop[0]].levels[S.size()-1]: " << map[pop[0]].levels[S.size() - 1] << endl;
							flip = mCMRbit - 1;
							flip.flip();

							map[i].CMlevels[SCM.size() - 1] &= flip;
							for (int k = pop[0] + 1; k < i; ++k) {
								//cout << "i " << i << " j " << pop[0] << " k " << k << endl;
								//cout << S.size() << endl;
								map[k].CMlevels[SCM.size() - 1].reset();
							}
						}
					}
				}
				mCMRight &= mCMRight - 1;						// remove the rightmost 1
			} while (mCMRbit != 0);
			* 
			*/
		}
		
	  for (a <- 0 until map.size) {
		  for (b <- lvls - 1 until 0 by -1) {
	  		val temp1 = map(a).levels(b);
	  		val temp2 = map(a).levels(b - 1);
  			map(a).levels(b) = temp1 - (temp1 & temp2);
		
	  	}
	  }
  }
  def generateColonPositions(start: Int, end: Int, level: Int): Vector[Int] = {
    var colonPositions = Vector.empty[Int];
    var mcolon: Bits = new Bits(0);
    for (i <- (start / B_INT) until ceil(end.toDouble / B_INT).toInt) {
		  mcolon = map(i).levels(level);
		  while (mcolon.bits != 0) {
		    val mBit  = (mcolon & -mcolon.bits) - 1;
			  var offset: Int = i * B_INT + mBit.count();
			  if (start <= offset && offset <= end) {
				  colonPositions = colonPositions:+(offset);
			  }
			  mcolon = mcolon & (mcolon - 1);
		  }
	  }
	  println("Colon Position is: ");
	  for (i <- 0 until colonPositions.length) {
		  print(colonPositions(i) + " ");
	  }
	  println();
	  return colonPositions;
  }
  override def toString: String = {
    var output: String = "";
    for(i <- 0 until word.size)
    {
      output += "String is: " + word(i);
      output += "\nPhase 1: ";
      output += "\n\\  bitset: " + map(i).escapeBitset;
      output += "\n\"  bitset: " + map(i).quoteBitset;
      output += "\n:  bitset: " + map(i).colonBitset;
      output += "\n{  bitset: " + map(i).lbracketBitset;
      output += "\n}  bitset: " + map(i).rbracketBitset;
      output += "\n,  bitset: " + map(i).commaBitset;
      output += "\n[  bitset: " + map(i).arraylbracketBitset;
      output += "\n]  bitset: " + map(i).arrayrbracketBitset;
      output += "\nPhase 2: ";
      output += "\nSQ bitset: " + map(i).structQBitset;
      output += "\nPhase 3: ";
      output += "\nstrbitset: " + map(i).strBitset;
      output += "\nPhase 4: ";
      output += "\nSC bitset: " + map(i).structCBitset;
      for(j <- 0 until map(i).levels.size)
      {
        output += "\nL" + j + ":        ";
        output += map(i).levels(j);
      }
      output += "\n\n";
    }
    return output;
  }
  // methods:off
}
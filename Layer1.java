
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import DataStructures.WordWordDecade;


public class Layer1 {

  public static class Layer1Mapper
       extends Mapper<Object, Text, WordWordDecade, LongWritable>{

	private Set<String> stopWords = null;
	
	/**
	 * Setup before mapping - Gets the stop words from the web and puts them in a HashSet
	 */
	public void setup(Context context) {
		//System.out.println("--------------MAPPER L1 SETUP: Get stop words from web----------------");
		stopWords = new HashSet<String>();
        URL url;
		try {
			url = new URL("http://tools.seobook.com/general/keyword-density/stop_words.txt");
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
			String inputLine;
	        while ((inputLine = in.readLine()) != null)
	        	stopWords.add(inputLine);
	        in.close();		        
		} catch (Exception e) {			
			e.printStackTrace();
			return;
		} 	    		
		//System.out.println(stopWords.toString());
	}
		
    public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
    	//System.out.println("#### MAPPING L1: " + value.toString());    	
    	String[] splitted = value.toString().split("\t");	
    	
    	// if broken line
    	if (splitted.length < 5)
    		return;
    	
    	int year = Integer.parseInt(splitted[1]);
    	if (year < 1900)
    		return;

    	// clean stop words and signs - save valid to validWords
    	String[] ngrams = splitted[0].split(" ");
    	if (ngrams.length < 2)
    		return;
    	
    	// save amount of ngram
    	long ngram_amount = Long.parseLong(splitted[2]);    
    	
    	ArrayList<String> validWords = new ArrayList<String>();
    	for (int i = 0; i < ngrams.length; i++) {
    		String word = cleanWord(ngrams[i]);
    		if (!word.isEmpty() && !stopWords.contains(word)) {
					validWords.add(word);
			}
    	}
    	//System.out.println("Valid words: " + validWords);
    	
    	//split the validWords to <key,val> for the Reducer
    	int size = validWords.size();
    	if(size > 1)
    	{    		
			int midWordIndex = size/2;
			String middleWord = validWords.remove(midWordIndex);			
			WordWordDecade wordMiddle = new WordWordDecade(middleWord, year);
			//System.out.println("Mapper Output wordMiddle: Key:" + wordMiddle.toString() + ", Value " + amount.toString());
			if (size > 2) {
				long duplicates = -1*ngram_amount*(size-2); // the middle word will be counted more than one, so we need to remove duplicates
				context.write(wordMiddle , new LongWritable(duplicates));
			}
			
			for(String word : validWords)
			{
				//<{middleWord,wi,decade},amount>
				// value of c(w,wi) or c(wi,w)
				WordWordDecade wordPair = new WordWordDecade(middleWord, word, year);
				//System.out.println("Mapper Output words: Key:" + wordPair.toString() + ", Value " + amount.toString());
				context.write(wordPair , new LongWritable(ngram_amount));						
			}
    	}
    }
    
    public String cleanWord(String word) {
    	word = word.toLowerCase();
		if (word.endsWith("'s"))
			word = word.substring(0,word.length()-2);
		return word.toLowerCase().replaceAll("[^a-z]", "");
    }    
  }

	public static class PartitionerClass extends Partitioner<WordWordDecade, LongWritable>
	{
		@Override
		public int getPartition(WordWordDecade key, LongWritable value, int numPartitions)
		{					
			int decadeToPrint = key.getDecade() % 12;
			//System.out.println("PartitionerClass L1 decadeToPrint:" + decadeToPrint);
			return decadeToPrint % numPartitions; //12 - num of decade from 1900 to 2020
		}
	}
  
  
  public static class LayerOneReducer extends Reducer<WordWordDecade,LongWritable,WordWordDecade,LongWritable> 
  {
    public void reduce(WordWordDecade key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException 
    {
       //System.out.println("$$ Reducing L1: " + key.toString());	
	   long sum = 0;
	   for (LongWritable val : values) 
	   {
	     sum += val.get();
	   }
	   LongWritable sumToPrint = new LongWritable(sum);
	   //System.out.println("Writing - Key: " + key.toString() + ", Value: " + sumToPrint.toString());
	   context.write(key, sumToPrint); 
    }
  }

  public static void main(String[] args) throws Exception {	 
	System.out.println("RUNNING L1");	
	System.out.println("args[0]:" + args[0].toString() + "; args[1]:" + args[1].toString() + "; args[2]:" + args[2].toString());
    Configuration conf = new Configuration();
    Job job = Job.getInstance(conf, "ass2");
    job.setJarByClass(Layer1.class);
    job.setMapperClass(Layer1Mapper.class);
    //job.setPartitionerClass(PartitionerClass.class);
    job.setCombinerClass(LayerOneReducer.class);
    job.setReducerClass(LayerOneReducer.class);
    job.setOutputKeyClass(WordWordDecade.class);
    job.setOutputValueClass(LongWritable.class);
    job.setInputFormatClass(SequenceFileInputFormat.class);
    FileInputFormat.addInputPath(job, new Path(args[1]));
    FileOutputFormat.setOutputPath(job, new Path(args[2]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }


}

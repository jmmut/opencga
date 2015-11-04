## Description

Given a list of `m` variants with a list of `n` genotypes each, corresponding to `n` individuals, we 
output ((n choose 2) times) this values:

````
[x] FID1   : family id of individual 1  
[x] IID1   : individual 1 id  
[x] FID2   : family id of individual 1  
[x] IID2   : individual 2 id  
[x] RT     : relationship type given PED file  
[ ] EZ     : expected IBD sharing given PED file  
[ ] Z0     : P(IBD=0)  
[ ] Z1     : P(IBD=1)      
[ ] Z2     : P(IBD=2)  
[ ] PI_HAT : P(IBD=2)+0.5*P(IBD=1) ( proportion IBD )  
[x] PHE    : Pairwise phenotypic code (1,0,-1 = AA, AU and UU pairs)  
[x] DST    : IBS distance (IBS2 + 0.5*IBS1) / ( N SNP pairs )  
[x] PPC    : IBS binomial test  
[X] RATIO  : Of HETHET : IBS 0 SNPs (expected value is 2)  
[x] IBS0   : Number of IBS 0 nonmissing loci  
[x] IBS1   : Number of IBS 1 nonmissing loci  
[x] IBS2   : Number of IBS 2 nonmissing loci  
[x] HOMHOM : Number of IBS 0 SNP pairs used in PPC test  
[x] HETHET : Number of IBS 2 het/het SNP pairs in PPC test  
````

- For each pair of individuals p1 and p2:
  - FID1, IID1, FID2, IID2, RT and PHE:
    These are trivially obtainable from the variant and/or ped file. Could be missing, though.
  
  - IBS0, IBS1, and IBS2:  
    Count number of shared alleles in each variant. For instance, genotypes 0/0 and 0/1 would be IBS1 because only a 0 
    is shared between both individuals.

  - DST:  
    Distance between both individuals, measured as euclidian distance, or as shared genotypes ratio.
    ```
    if ( par::cluster_euclidean )
        dst = sqrt((IBSg.z1*0.5 + IBSg.z2*2)/(IBSg.z0+IBSg.z1+IBSg.z2*2));
    else 
        dst = (IBSg.z1*0.5 + IBSg.z2)/(IBSg.z0+IBSg.z1+IBSg.z2);
    ```

  - HOMHOM, HETHET:  
    Count of IBS0 and IBS2 respectively that can be considered independent: they are in different chromosomes or farther away than
    a ppc-gap (500000 base pairs by default).
    
  - RATIO:  
    Using HOMHOM and HETHET, for a given pair, we expect to see autosomal SNPs with two 
    copies of each allele occur in a 2:1 ratio of IBS 2 to IBS 0.
    ```
    ratio = pvIBS2het/pvIBS0;
    ```

  - PPC:  
    Pairwise Population Concordance. Compute p-value of the previous ratio.
    ```
    // n.b. 2/9 is 2/3 * (1-2/3)
    z = (pvIBS2het/(pvIBS0+pvIBS2het) - (2.0/3.0)) 
           / (sqrt((2.0/9.0)/(pvIBS0+pvIBS2het)))
    pvalue = normdist(z);
    ```
  - Z0:  
    Probability of Identity By Descent = 0. In a variant, looking at the genotypes, there will be a certain amount of 
    reference and alternate alleles. Let `p` and `q` be the ratios of `ref/(ref+alt)` and `alt/(ref+alt)` across all the 
    variants.
    
    **Implementation note:** `p` and `q` require looping across all variants and all genotypes. Maybe this should be 
    done while counting IBS0, IBS1 and IBS2.
    
    If the 
    alleles were uniformly distributed across genotypes, we should expect a certain amount of IBS0. In two individuals 
    (4 alleles), all the permutations are `2^4 = 16` different genotypes, and two of them are IBS0: 
    {0/0, 1/1} and {1/1, 0/0}. Intuitively, the probability of each of those pairs is `p*p*q*q` and `q*q*p*p`, so 
    `P(IBS0) = 2*p*p*q*q`.
  

## Bibliography

- https://www.ncbi.nlm.nih.gov/pmc/articles/PMC1950838/


#!/bin/bash


/home/jmmut/usr/plink-1.9/plink --genome full --vcf ~/appl/opencga/ibd/test.vcf > /dev/null
mv plink.genome p19vcf.genome

echo "diff: (for further details: meld test.genome p19vcf.genome & )"
diff test.genome p19vcf.genome


/home/jmmut/usr/plink-1.9/plink --genome full --file ~/appl/opencga/ibd/test > /dev/null
mv plink.genome p19ped.genome

echo "diff: (for further details: meld p19ped.genome p19vcf.genome & )"
diff p19ped.genome p19vcf.genome


/home/jmmut/usr/plink-1.07-x86_64/plink --genome --file ~/appl/opencga/ibd/test --noweb --genome-full > /dev/null
mv plink.genome p107ped.genome


echo "diff: (for further details: meld p19ped.genome p107ped.genome & )"
diff p19ped.genome p107ped.genome




## TODO test our version too



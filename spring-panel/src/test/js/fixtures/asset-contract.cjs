'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
// Check current resource wiring, independent of historical cache-key dates.
function script(html,file){
  const pattern=new RegExp('/js/'+file.replaceAll('.','\\.')+'(?:[?(}]|$)');
  const tags=[...html.matchAll(/<script\b[^>]*>/g)].filter(([tag])=>{
    const attribute=tag.match(/\b(?:th:)?src="([^"]+)"/);
    return attribute&&pattern.test(attribute[1]);
  });
  assert.equal(tags.length,1,'Expected exactly one script: '+file);
  assert(fs.statSync(path.resolve(__dirname,'../../../main/resources/static/js',file)).isFile(),file);
  return tags[0].index;
}
module.exports={script};

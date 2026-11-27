'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
module.exports=function(overrides={}){
  const window={...overrides};
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../../../main/resources/static/js/panel-errors.js'),'utf8'),{window});
  return window;
};

/* Verify or restore retired recovery snapshots. Never opens a database or overwrites files. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
function argument(name){const at=process.argv.indexOf(name);return at<0?undefined:process.argv[at+1];}
function inside(base,name){assert(!path.isAbsolute(name)&&!name.split(/[\\/]/).includes('..'),'Unsafe archive entry');const file=path.resolve(base,name);assert(file.startsWith(path.resolve(base)+path.sep),'Archive path escaped its root');return file;}
async function sha256(file){const sum=crypto.createHash('sha256');for await(const chunk of fs.createReadStream(file))sum.update(chunk);return sum.digest('hex');}
async function main(){
 const archive=argument('--archive');if(!archive)throw Error('Usage: node tools/recovery-archive.cjs --archive <external archive root> [--only <original path>] [--restore-to <separate directory>]');
 const archiveRoot=fs.realpathSync(path.resolve(archive));
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'docs/architecture/recovery-archive-330.json'),'utf8'));
 const only=argument('--only'),entries=manifest.files.filter(entry=>!only||entry.path===only);assert(entries.length,'Requested entry is not in the manifest');
 let restored=0,bytes=0;const destination=argument('--restore-to');let targetRoot;
 if(destination){
  const resolved=path.resolve(destination);assert(resolved!==path.parse(resolved).root,'Restore into a separate named directory');
  assert(resolved!==root&&!resolved.startsWith(root+path.sep),'Restore outside the checkout, then review the files before copying anything back');
  fs.mkdirSync(resolved,{recursive:true});targetRoot=fs.realpathSync(resolved);
 }
 for(const entry of entries){
  const source=inside(archiveRoot,entry.path);assert.equal(fs.realpathSync(source),source,'Archive symlinks are not accepted');
  assert.equal(fs.statSync(source).size,entry.bytes,entry.path+' size');assert.equal(await sha256(source),entry.sha256,entry.path+' checksum');
  bytes+=entry.bytes;
  if(targetRoot){
   const file=inside(targetRoot,entry.path);fs.mkdirSync(path.dirname(file),{recursive:true});
   assert(fs.realpathSync(path.dirname(file)).startsWith(targetRoot+path.sep),'Restore parent escaped destination');
   if(fs.existsSync(file))assert.equal(await sha256(file),entry.sha256,'Refusing to overwrite '+entry.path);
   else{fs.copyFileSync(source,file,fs.constants.COPYFILE_EXCL);assert.equal(await sha256(file),entry.sha256);restored++;}
  }
 }
 console.log(JSON.stringify({verified:entries.length,bytes,restored,archive:archiveRoot,...(targetRoot?{destination:targetRoot}:{})}));
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});

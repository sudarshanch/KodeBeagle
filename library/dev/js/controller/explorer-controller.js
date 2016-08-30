(function() {
  var app = angular.module( 'FileExplorer', [ 'KodeBeagleFileExplore'] );
  app.controller( 'explorerController', [
    '$scope',
    '$location',
    'model',
    'http',
    function(
        $scope,
        $location,
        model,
        http
    ) {
        /*fileMetaInfo is metadata received from Kodebeagle server,
            fileMetadata is parsed and converted to lineMetaInfo that is a 
            map from lineNumber to all types information*/

        var lineMetaInfo = {}, fileMetaInfo = {};

        /*linesMeta & filesMeta is meta information stored in session object of external files 
            we get from KodeBeagle server being called on each type reference click*/

        var linesMeta = {},filesMeta = {};
        var loader;
        var fileName = document.location.search.split('?filename=')[1];

        init();

        function init() {
          var encodedFileName = encodeURIComponent(fileName);
          var url = model.config.esURL
                    + '/source'
                    + '/'+ encodedFileName;
          http.get(url)
          .then(fileSource,sourceFailureHandler);

          http.get(model.config.esURL + "/filedetails/" + encodedFileName)
          .then(fileDetailsHandler, fileDetailsFailureHandler);
        }

        function sourceFailureHandler(error) {
          $scope.showMessage = true;
          $scope.dataLoaded = false;
        }

        function fileDetailsFailureHandler(error) {
          $scope.detailsMessage = true;
          $scope.detailsLoaded = false;
        }

        function fileDetailsHandler(res) {
          $scope.detailsMessage = true;
          if (!res) {
            $scope.detailsLoaded = false;
            return;
          }

          if (res && res.length > 0) {
            $scope.detailsLoaded = true;
            $scope.repoInfo = res[0];  

            if ($scope.repoInfo) {
              if ($scope.repoInfo.commits) {
                $scope.commits = $scope.repoInfo.commits;
                $scope.commits.forEach(function(details){
                  details.time = getElapsedTime(details.time);
                });
              }
              if ($scope.repoInfo.coChange) {
                var changes = [];
                $scope.repoInfo.coChange.forEach(function(eachChange){
                  var filePath = eachChange.split('/');
                  var coChange = {};
                  var path;
                  if (filePath.length > 2) {
                    path = filePath[0]+'/'+filePath[1]+'/../'+filePath[filePath.length-1];
                  } else {
                    path = filePath[0]+'/../'+filePath[filePath.length-1];
                  }
                  coChange.fileName = path;
                  if (eachChange.split('/') && eachChange.split('/').length > 0) {
                    var arrPath = fileName.split(eachChange.split('/')[0]);
                    if (arrPath && arrPath.length > 0)
                      eachChange = arrPath[0] + eachChange;
                  }
                  coChange.filePath = eachChange;
                  changes.push(coChange);
                });
                $scope.coChanges = changes;
              }
              if ($scope.repoInfo.topAuthors) {
                $scope.topAuthors = $scope.repoInfo.topAuthors;
              }
            }
          }
          
          /*$scope.commits = $scope.repoInfo.metricsForChart.filter(function(eachMetric){
            eachMetric.name = new Date(eachMetric.name);
            return eachMetric.name > new Date(new Date().setFullYear(new Date().getFullYear() - 1));
          });*/
          $scope.data = {
            commits: $scope.commits
          }
          $scope.options = {
            margin: {top: 20, right:0},
            series: [
              {
                  axis: "y",
                  dataset: "commits",
                  key: "count",
                  color: "#1f77b4",
                  type: ["line"],
                  label: "Commits in the repository",
                  id: 'repoCommits'
              }
            ],
            axes: {
              x: {
                key: "name",
                type: "date"
              }
            }
          }
        }

        function getElapsedTime(timeStamp) {
          var date = new Date(0);
          date.setUTCSeconds(timeStamp);
          var seconds = Math.floor((new Date() - date) / 1000);
          var interval = Math.floor(seconds / 31536000);

          if (interval > 1) {
              return interval + " years ago";
          }
          interval = Math.floor(seconds / 2592000);
          if (interval > 1) {
              return interval + " months ago";
          }
          interval = Math.floor(seconds / 86400);
          if (interval > 1) {
              return interval + " days ago";
          }
          interval = Math.floor(seconds / 3600);
          if (interval > 1) {
              return interval + " hours ago";
          }
          interval = Math.floor(seconds / 60);
          if (interval > 1) {
              return interval + " minutes ago";
          }
          return Math.floor(seconds) + " seconds ago";
        }
        
        function fileSource(res) {
          $scope.showMessage = true;
          if (!res) {
            $scope.dataLoaded = false;
            return;
          }

          if (res) {
            $scope.dataLoaded = true;

            var fileSplit = fileName.split('/');
            var fileDisplay = fileSplit[0]+'/'+fileSplit[1]+'/../'+fileSplit[fileSplit.length-1];
            $scope.fileInfo = {
              fileName: fileDisplay,
              fileContent: res
            }
            setTimeout(function(){
              addColumnNumbers();
            },50);
          }
        }

        function getMetaInfo() {
          if(model.sessionStorage.getValue('linesMeta') && model.sessionStorage.getValue('linesMeta')[fileName]){
            fileMetaInfo = model.sessionStorage.getValue('filesMeta')[fileName];
            lineMetaInfo = model.sessionStorage.getValue('linesMeta')[fileName];
            navigateToSelection(fileMetaInfo);
            highliteReferences(lineMetaInfo);

            model.sessionStorage.deleteValue("filesMeta");
            model.sessionStorage.deleteValue("linesMeta");
          } else {
            var encodedFileName = encodeURIComponent(fileName);
            var url = model.config.esURL + "/metadata/file/"+encodedFileName;
            http.get(url)
            .then(processMetaInfo);
          }
        }

        function processMetaInfo(response) {
          var metadata = response;
          if(metadata){
            navigateToSelection(metadata[0]);
            parseFileMetadata(metadata);
            fileMetaInfo = filesMeta[fileName];
            lineMetaInfo = linesMeta[fileName];
            highliteReferences(lineMetaInfo);
          }
        }

        function columnCount(text){
          if(!text){
            return 0;
          }

          var colCount = 0;
          if(text.search('\t') > -1){
              while(text.search('\t') > -1){
                  text = text.replace('\t','');
                  colCount++;
              }
          } else {
            var arrTexts = text.split(' ');
            for(var eachIndex=0;eachIndex<arrTexts.length;eachIndex++){
              if(arrTexts[eachIndex]){
                  break;
              }
              colCount++;
            }
          }
          return colCount;
        }

        function addColumnNumbers(){

          $('.eachrow').each(function(rowIndex){
            var colCount = 0;
            var importText = $(this).text();
            if (importText.indexOf('import') > -1){
              var splitImports = importText.split(' ');
              $(this).empty();
              var emptySpan = document.createElement('span');
              $(emptySpan).addClass('kwd');
              $(emptySpan).attr('data-column-number',colCount);
              emptySpan.innerHTML = splitImports[0]+' ';
              this.appendChild(emptySpan);
              colCount += splitImports[0].length+1;
              var emptySpan = document.createElement('span');
              $(emptySpan).attr('data-column-number',colCount);
              emptySpan.innerHTML = splitImports[1];
              this.appendChild(emptySpan);
            } else {
              $(this).find('span').each(function(index){
                if(index === 0 && $(this).text().trim()) {
                    var doc = document.createDocumentFragment(), 
                        text = $(this).text(), spaces = columnCount(text);
                    
                    if(spaces > 0){
                        var emptySpan = document.createElement('span');
                        emptySpan.innerHTML = text.substr(0,spaces);
                        doc.appendChild(emptySpan);
                        $(doc).insertBefore(this);
                        colCount += spaces;
                        $(this).text(text.substr(spaces,text.length));     
                    }
                    $(this).attr('data-column-number', colCount);
                    colCount += text.length - spaces;
                }
                /*else {
                  $(this).attr('data-column-number', colCount);
                  colCount += $(this).text().length;
                }
                
                if(this.nextSibling){
                  var text = this.nextSibling.innerHTML;
                  var spaces = columnCount(text);
                  spaces = text.trim().length>0 ?spaces:0;
                  colCount += spaces;
                }*/
              });
            }
          });
          getMetaInfo();
        }

        /*Navigate to method selection from session storage*/

        function navigateToSelection(fileMetadata){
          if(model.sessionStorage.getValue('methodInfo')){
            fileMetadata.methodDefinitionList.some(function(methodDef) {
              var sessionMethodInfo = model.sessionStorage.getValue('methodInfo');
              if(isMethodDefEqual(methodDef,sessionMethodInfo)){
                var lineInfo = methodDef.loc;
                scrollAndSelect(lineInfo);
              }
            });
            model.sessionStorage.deleteValue("methodInfo");
          }
        }

        /*Check if method definations are equal based on name, arguments & argument types*/

        function isMethodDefEqual(methodDef1,methodDef2){
          if(methodDef1.method !== methodDef2.method){
            return false;
          }else if (methodDef1.argTypes.length !== methodDef2.argTypes.length) {
            return false;
          }else{
            for(var i = 0; i < methodDef2.argTypes.length; i++) {
                if(methodDef1.argTypes[i] !== methodDef2.argTypes[i])
                    return false;
            }
            return true;
          }
        }

        /*Converts meta data from KodeBeagle endpoint to lines of meta information 
            for each github file visited*/

        function parseFileMetadata(metaData) {
          linesMeta = {},filesMeta = {};
          var hits = metaData;
          hits.forEach(function(eachHit){
            var lineMetadata = {};
            eachHit.externalRefList.forEach(function(external){
              var externalVars = angular.copy(external);
              externalVars.vars.forEach(function(eachVar){
                externalVars.type = 'externalRefType';
                if(!lineMetadata[eachVar[0]])
                  lineMetadata[eachVar[0]] = [];
                lineMetadata[eachVar[0]].push(externalVars);
              });
              var externalMethods = angular.copy(external)
              externalMethods.methods.forEach(function(method){
                externalMethods.type = 'externalRefMethod';
                method.loc.forEach(function(eachLoc){
                  if(!lineMetadata[eachLoc[0]])
                    lineMetadata[eachLoc[0]] = [];
                  lineMetadata[eachLoc[0]].push(externalMethods);  
                });
              });
            });
            eachHit.internalRefList.forEach(function(internalRef, index){
              internalRef.type = "internalRefChild";
              internalRef.c.forEach(function(ref){
                if(!lineMetadata[ref[0]])
                  lineMetadata[ref[0]] = [];
                lineMetadata[ref[0]].push(internalRef);
              });
              var parent = angular.copy(internalRef);
              if(parent.p && parent.p.length > 0 ) {
                if(!lineMetadata[parent.p[0]])
                  lineMetadata[parent.p[0]] = [];
                parent.type = "internalRefParent";
                lineMetadata[parent.p[0]].push(parent);
              }
            });
            linesMeta[eachHit.fileName] = lineMetadata;
            filesMeta[eachHit.fileName] = eachHit;
          }); 
          //model.sessionStorage.setValue('linesMeta',linesMeta);
          //model.sessionStorage.setValue('filesMeta',filesMeta);
        }

        /*Adds the css style to code based on lines meta information*/

        function highliteReferences(lineMetadata){
          for(var eachLine in lineMetadata){
            lineMetadata[eachLine].forEach(function(eachColumn){
              if(eachColumn.type === 'internalRefChild'){
                eachColumn.c.forEach(function(eachColumn){
                  createLinks(eachColumn);
                });
              } else if(eachColumn.type === 'internalRefParent'){
                createLinks(eachColumn.p);
              } else if (eachColumn.type === 'externalRefMethod') {
                  eachColumn.methods.forEach(function(method){
                    createLinks(method.loc[0]);  
                  });
              } else if (eachColumn.type === 'externalRefType') {
                  eachColumn.vars.forEach(function(eachVar){
                    createLinks(eachVar);  
                  });
              }
              else{
                createLinks(eachColumn.loc.split('#'));
              }
            });
          }
        }

        function createLinks(lineInfo){
          var element = $("li[data-line-number="+ lineInfo[0] +"]").find("span[data-column-number="+lineInfo[1]+"]")[0];
          $(element).addClass('referenced-links');
        }

        function closePopUp(){
          var elements = document.getElementsByClassName("links-box");
          while (elements[0]) {
            elements[0].parentNode.removeChild(elements[0]);
          }
        }

        $scope.getMatchedTypes = function(event) {
          closePopUp();
          
          var target = $(event.target).hasClass('referenced-links')?event.target:$(event.target.parentNode).hasClass('referenced-links')?event.target.parentNode:null;
          if(target) {
            clearBoldLinks();

            var lineNumber = target.parentNode.attributes["data-line-number"].value,
                  lineMeta = lineMetaInfo[lineNumber], sourceFile = "";

            if(!lineMeta)
              return;
            
            lineMeta.some(function(typeInfo){

              if(typeInfo.type == "externalRefType") {
                var typeVars = typeInfo.vars;
                    columnValue = target.attributes['data-column-number'];

                typeVars.forEach(function(eachVar){
                  if(columnValue && columnValue.value == eachVar[1]){ 
                    sourceFile = typeInfo.fqt;
                    return true;
                  }
                });
              }

              if(typeInfo.type == "externalRefMethod") {
                var methods = typeInfo.methods;
                    columnValue = target.attributes['data-column-number'];

                methods.forEach(function(method){
                  method.loc.forEach(function(eachLoc){
                    if(columnValue && columnValue.value == eachLoc[1]){ 
                      sourceFile = typeInfo.fqt;
                      model.sessionStorage.setValue('methodInfo',method);
                      return true;
                    }
                  });
                });
              }

              if(typeInfo.type == "internalRefChild") {
                var lineInfo = typeInfo.p, childLinesInfo = typeInfo.c,
                    columnValue = target.attributes['data-column-number'].value;                

                childLinesInfo.forEach(function(childLine){
                  if(columnValue == childLine[1] && childLine[0] != lineInfo[0]){    
                    scrollAndSelect(lineInfo,target);
                    return true;
                  }
                });
              }

              if(typeInfo.type == 'internalRefParent') {
                var childLines = typeInfo.c, internalReferences = [],
                columnValue = target.attributes['data-column-number'].value;
                if(columnValue == typeInfo.p[1]){
                  childLines.forEach(function(eachLine){
                    var line = eachLine,refereceObj = {}, 
                        child = $("li[data-line-number="+line[0]+"]"),
                        codeSnippet = child.text().trim(),
                        selectedText = target.innerHTML.trim();
                    codeSnippet = codeSnippet.replace(selectedText,"<b>"+selectedText+"</b>");
                    createBoldLink(child,selectedText);
                    refereceObj.snippet = codeSnippet;
                    refereceObj.references = eachLine;
                    internalReferences.push(refereceObj);
                  });
                  showInternalReferences(internalReferences,{left:event.pageX, top:event.pageY+10});
                  return true;
                }
              }
            });

            if(sourceFile) {
              //sourceFile = sourceFile.replace(/\./g, "/");
              sourceFile = sourceFile.replace(/\[\]/g, "");
              getMatchedSourceFiles(sourceFile, event);
            }
          }
        }

        function createBoldLink(child,selectedText){
          $(child).find('.referenced-links').each(function(){
              this.innerHTML = $(this).text().replace(selectedText,"<b>"+selectedText+"</b>");
          });
        }

        function clearBoldLinks(){
          $('.referenced-links b').each(function(){
              $(this).contents().unwrap();
          });
        }

        function scrollAndSelect(lineInfo,target){
          var rowEle = $("li[data-line-number="+ lineInfo[0] +"]"),element;
          $('html,body').animate({
              scrollTop: rowEle.offset().top - 20
          }, 500);
          selectRow(rowEle);
          element = rowEle.find("span[data-column-number="+lineInfo[1]+"]")[0];
          selectText(element);
        }

        function selectRow(rowEle){
          $('.select-row-color').removeClass('select-row-color');
          rowEle.addClass('select-row-color');
        }

        function selectText(element){
          if(element){
            var selection = window.getSelection(),range = document.createRange();
            range.selectNodeContents(element);
            selection.removeAllRanges();
            selection.addRange(range);
          }
        }

        function getMatchedSourceFiles(sourceFile, event) {
          var encodedFileName = encodeURIComponent(fileName);
          var url = model.config.esURL + "/metadata/type/"+sourceFile;
          http.get(url)
          .then(function(response) {
            hideLoader(event);
            var extRes = response;
            showDropDown(extRes, {left:event.pageX, top:event.pageY+10});
            parseFileMetadata(extRes);
          });
        }

        function hideLoader(event){
          $('.reference-loader').remove();    
        }

        var showLoader = function(event){
          event.target.parentNode.appendChild(loader);
        };

        function showInternalReferences(internalReferences, position) {
          var div = createInternalRefDiv(internalReferences);
          document.body.insertBefore(div, document.body.firstChild);
          $(div).offset(position);
        }

        function showDropDown(matchedSourceFiles, position) {
          var div = createExternalRefDiv(matchedSourceFiles);
          document.body.insertBefore(div, document.body.firstChild);
          $(div).offset(position);
        }

        function createExternalRefDiv(matchedSourceFiles) {
          var doc = document.createDocumentFragment(), div = document.createElement ("div");
          div.className = "links-box";
          if(matchedSourceFiles.length === 0){
            var span = document.createElement("span");
            span.className = 'external-ref';
            span.textContent = "Couldn't find external references";
            doc.appendChild(span);
          }else{
            matchedSourceFiles.forEach(function(sourceFile) {
              var a = document.createElement("a");
              a.className = 'external-ref';
              a.textContent = sourceFile.fileName;
              a.href = "./../explore?filename="+sourceFile.fileName;
              doc.appendChild(a);
            });
          }
          div.appendChild(doc);
          return div;
        }

        function gotoLine(event){
          var lineInfo = event.currentTarget.attributes['data-line-info'].value;
          closePopUp();
          scrollAndSelect(lineInfo.split(','));
        }

        function createInternalRefDiv(references) {
          var doc = document.createDocumentFragment(), mainDiv = document.createElement("div");
          mainDiv.className = "links-box";
          
          if(references.length === 0){
            var span = document.createElement("span");
            span.className = 'external-ref';
            span.textContent = "Couldn't find external/internal references";
            doc.appendChild(span);
          }else{
            var references = references.sort(function(ref1,ref2){
              return ref1.references[0] > ref2.references[0];
            });
            references.forEach(function(lineInfo) {
              var lineDetails = lineInfo.references,
                  div = document.createElement("div");
              div.setAttribute('data-line-info',lineInfo.references);
              div.onclick = gotoLine;
              var lineSpan = document.createElement("span");
              lineSpan.style.width = '80px';
              lineSpan.textContent = lineDetails[0]+":"+lineDetails[1];
              div.appendChild(lineSpan);
              var textSpan = document.createElement("span");
              textSpan.innerHTML = lineInfo.snippet;
              div.appendChild(textSpan);
              doc.appendChild(div);
            });
          }
          mainDiv.appendChild(doc);
          return mainDiv;
        }

        var cloneLoader = function(){
          loader = $('.page-context-loader').clone().addClass('reference-loader')[0];
        };
    }
  ]);

  angular.bootstrap( document, [ 'FileExplorer' ] );
})();
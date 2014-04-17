function(doc) {
  if(doc.fetched && (!doc["last-fetched-resp"] in [200,201, 300,301,302])) {
    emit(doc.sid, doc);
  }
}

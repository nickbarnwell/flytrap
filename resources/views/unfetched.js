function(doc) {
  if(!doc.fetched) {
    emit(doc.sid, doc)
  }
}

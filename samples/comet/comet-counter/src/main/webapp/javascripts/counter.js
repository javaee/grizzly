var counter = {
      'poll' : function() {
         new Ajax.Request('long_polling', {
            method : 'GET',
            onSuccess : counter.update
         });
      },
      'increment' : function() {
         new Ajax.Request('long_polling', {
            method : 'POST'
         });
      },
      'update' : function(req, json) {
         $('count').innerHTML = json.counter;
         counter.poll();
      }
}

var rules = {
      '#increment': function(element) {
         element.onclick = function() {
            counter.increment();
         };
      }
};

Behaviour.register(rules);
Behaviour.addLoadEvent(counter.poll);

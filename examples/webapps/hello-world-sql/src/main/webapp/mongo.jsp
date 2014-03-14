<%@ page import="com.mongodb.*" %>

<html>
<head>
    <title>Sample Application MongoDB JSP Page</title>
</head>
<body bgcolor=white>

<table border="0">
    <tr>
        <td align=center>
            <img src="images/bridge-small.png">
        </td>
        <td>
            <h1>Sample Brooklyn Deployed WebApp (MongoDB JSP)</h1>
            This is the output of a JSP page that is part of the Hello, World application,
            deployed by brooklyn, to show <b>MongoDB database interactivity</b>.
        </td>
    </tr>
</table>

<%
String port=System.getProperty("brooklyn.example.mongodb.port");
//URL should be supplied e.g. ""-Dbrooklyn.example.db.url=jdbc:mysql://localhost/visitors?user=brooklyn&password=br00k11n"
//(note quoting needed due to ampersand)
if (port==null) {
%>
<p>(There is no database included as part of this example.)</p>
<% } else { %>
<br/>
<p>Visitors:</p>
<ul>

<%
/* begin database-enabled block */ }
MongoClient client = new MongoClient("localhost", new Integer(port));
DB database = client.getDB("visitors");
DBCollection messages =  database.getCollection("messages");
int i=0;

if (request.getParameter("name") != null) {
    // add a message
    DBObject newMessage = new BasicDBObject();
    newMessage.put("name", request.getParameter("name"));
    newMessage.put("message", request.getParameter("message"));
    messages.save(newMessage);
}

DBCursor messageCursor = messages.find();

try {
    while (messageCursor.hasNext()) {
        DBObject message = messageCursor.next();
        i++;
        %>
            <li><b><%=message.get("name")%></b>: <%=message.get("message")%></li>
        <%
    }
} finally {
    messageCursor.close();
}

if (i==0) {
%>
    <li><i>None</i></li>
<%
}

%>

</ul>

<br/>

<p>Please enter a message:</p>

<form action="mongo.jsp" method="GET">
    <table>
        <tr><td>Name: </td><td><input type="text" name="name"></td></tr>
        <tr><td>Message: </td><td><input type="text" name="message"></td></tr>
    </table>
    <input type="submit" value="Submit"/>
</form>

<br/>
<p>Click <a href="index.html">here</a> to go back to the main page.</p>
</body>
</html>
<!DOCTYPE html>
<html lang="en">
  <head>
    <title>Unity</title>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- Bootstrap -->
    <link href="http://getbootstrap.com/dist/css/bootstrap.min.css" rel="stylesheet">

    <link href="/css/core.css" rel="stylesheet">

  </head>
  <body onload="loadPage()">

    <header class="pull-right">

      <div id="ip" class="hide">${ip}</div>
      <div id="uuid" class="hide">${uuid}</div>

      <div class="btn-group" role="group" aria-label="...">
        <a href="#" class="btn btn-default">
          <span class="glyphicon glyphicon-th-list" aria-hidden="true"></span>
        </a>
        <a href="/unity" class="btn btn-default active">
          <span class="glyphicon glyphicon-th-large" aria-hidden="true"></span>
        </a>
      </div>
    </header>

  	<section>

      <div class="panel-group" id="accordion" role="tablist" aria-multiselectable="true">

        <div class="panel panel-default">
          <div class="panel-heading" role="tab" id="headingOne01">
            <h4 class="panel-title">
              <table>
                <tbody>
                  <tr>
                    <td>
                      <a role="button" data-toggle="collapse" class="link-panel-header" data-parent="#accordion" href="#collapseOne01" aria-expanded="true" aria-controls="collapseOne01">
                      </a>
                    </td>
                    <td>
                    </td>
                  </tr>
                </tbody>
              </table>
            </h4>
          </div>
          <div id="collapseOne01" class="panel-collapse collapse in" role="tabpanel" aria-labelledby="headingOne01">
            <div class="panel-body" id="elements">
<!--
              <table class="item">
                <tbody>
                  <tr>
                    <td rowspan="2" class="item-row-header item-width-icon">
                      <img src="http://www.exeterstreethall.org/wp-content/uploads/2014/05/txt.png" alt="...">
                    </td>
                    <td class="item-row-header">
                      <a href="#" class="link-open-file">server.log</a>
                    </td>
                    <td rowspan="2" class="item-row-header item-width-options">
                      <a href="#" class="link-delete hide">Eliminar</a>
                    </td>
                  </tr> 
                  <tr>
                    <td class="item-row-footer">
                      <small>24.6kb</small>
                    </td>
                  </tr>
                </tbody>
              </table>

              <hr>

              <table class="item">
                <tbody>
                  <tr>
                    <td rowspan="2" class="item-row-header item-width-icon">
                      <img src="http://www.camarachoco.org.co/sites/default/files/images/pdf.png" alt="...">
                    </td>
                    <td class="item-row-header">
                      <a href="#" class="link-open-file">invierno 2234.pdf</a>
                    </td>
                    <td rowspan="2" class="item-row-header item-width-options">
                      <a href="#" class="link-delete hide">Eliminar</a>
                    </td>
                  </tr> 
                  <tr>
                    <td class="item-row-footer">
                      <small>4.5kb</small>
                    </td>
                  </tr>
                </tbody>
              </table>

              <hr>

              <table class="item item-muted">
                <tbody>
                  <tr>
                    <td rowspan="2" class="item-row-header item-width-icon">
                      <img src="http://www.exeterstreethall.org/wp-content/uploads/2014/05/xls.png" alt="...">
                    </td>
                    <td class="item-row-header">
                      <a href="#" class="link-open-file">estadisticas verano 2345.xls</a>
                    </td>
                    <td rowspan="2" class="item-row-header item-width-options">
                      <a href="#" class="link-delete hide">Eliminar</a>
                    </td>
                  </tr>
                  <tr >
                    <td class="item-row-footer">
                      <small>7.3kb (el archivo esta siendo usado por <span title="V-131:Windows">Nacho</span> desde hace 3 minutos)</small>
                    </td>
                  </tr>
                </tbody>
              </table>
-->
            </div>
          </div>
        </div>

      </div>

  	</section>

    <!-- ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// -->

    <!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js"></script>
    <!-- Include all compiled plugins (below), or include individual files as needed -->
    <script src="http://getbootstrap.com/dist/js/bootstrap.min.js"></script>
    <!-- blockUI -->
    <script src="http://malsup.github.io/jquery.blockUI.js" type="text/javascript"></script>
    <!-- user js -->
  	<script src="/js/unity.js" type="text/javascript"></script>

  </body>
</html>
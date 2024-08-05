# AWS::ControlTower::LandingZone

Definition of AWS::ControlTower::LandingZone Resource Type

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::ControlTower::LandingZone",
    "Properties" : {
        "<a href="#manifest" title="Manifest">Manifest</a>" : <i>Map</i>,
        "<a href="#version" title="Version">Version</a>" : <i>String</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::ControlTower::LandingZone
Properties:
    <a href="#manifest" title="Manifest">Manifest</a>: <i>Map</i>
    <a href="#version" title="Version">Version</a>: <i>String</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### Manifest

_Required_: Yes

_Type_: Map

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Version

_Required_: Yes

_Type_: String

_Minimum Length_: <code>3</code>

_Maximum Length_: <code>10</code>

_Pattern_: <code>\d+.\d+</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the LandingZoneIdentifier.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### LandingZoneIdentifier

Returns the <code>LandingZoneIdentifier</code> value.

#### Arn

Returns the <code>Arn</code> value.

#### Status

Returns the <code>Status</code> value.

#### LatestAvailableVersion

Returns the <code>LatestAvailableVersion</code> value.

#### DriftStatus

Returns the <code>DriftStatus</code> value.
